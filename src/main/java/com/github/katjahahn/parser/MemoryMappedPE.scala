/**
 * *****************************************************************************
 * Copyright 2014 Katja Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.katjahahn.parser

import java.io.RandomAccessFile
import java.nio.file.Files
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import com.github.katjahahn.parser.optheader.OptionalHeader
import com.github.katjahahn.parser.sections.SectionLoader
import com.github.katjahahn.parser.sections.SectionTable
import MemoryMappedPE._
import java.io.File

/**
 * Represents the PE file content as it is mapped to memory.
 * <p>
 * The bytes are read from file only on request, making it possible to map large files.
 * Only maps section bytes for now. No file headers.
 *
 * @author Katja Hahn
 *
 * @param mappings A list of section mappings
 * @param data the PEData instance of the file
 */
class MemoryMappedPE(
  private val mappings: List[Mapping],
  private val data: PEData) {

  /**currently only used for indexWhere and indexOf**/
  private val chunkSize = 512

  /**Array-like methods**/

  /**
   * Returns byte at position i.
   * <p>
   * Scala method. Use get() for Java.
   * @param i index/position
   * @return byte at position i
   */
  def apply(i: Long): Byte = {
    val mapping = mappings.find(m => isWithin(i, m.va))
    mapping match {
      case Some(m) => readByteAt(m, i)
      case None => 0.toByte
    }
  }

  /**
   * Returns byte at position i.
   *
   * @param i index/position
   * @return byte at position i
   */
  def get(i: Long): Byte = apply(i)

  /**
   * Returns whether the value is within the range
   */
  private def isWithin(value: Long, range: Range): Boolean =
    range.start <= value && range.end >= value

  /**
   * Returns the byte at the virtual offset.
   * Requires the offset to be within the mapping. See {@link #isWithin}
   */
  private def readByteAt(m: Mapping, virtOffset: Long): Byte = {
    require(isWithin(virtOffset, m.va))
    val pStart = m.physA.start
    val relOffset = virtOffset - m.va.start
    val readLocation = pStart + relOffset
    val file = data.getFile
    using(new RandomAccessFile(file, "r")) { raf =>
      raf.seek(readLocation)
      raf.readByte()
    }
  }

  /**
   * Returns size number of bytes at the virtual offset from mapping m.
   * Requires the offset + size to be within the mapping. See {@link #isWithin}
   */
  private def readBytesAt(m: Mapping, virtOffset: Long, size: Int): Array[Byte] = {
    require(isWithin(virtOffset, m.va) && isWithin(virtOffset + size, m.va))
    val pStart = m.physA.start
    val relOffset = virtOffset - m.va.start
    val readLocation = pStart + relOffset
    val file = data.getFile
    using(new RandomAccessFile(file, "r")) { raf =>
      raf.seek(readLocation)
      val length = (Math.min(readLocation + size, file.length) - readLocation).toInt
      val bytes = zeroBytes(length)
      raf.readFully(bytes)
      bytes ++ zeroBytes(size - length)
    }
  }

  private def using[A, B <: { def close(): Unit }](closeable: B)(f: B => A): A =
    try { f(closeable) } finally { closeable.close() }

  /**
   * Returns the size of the memory mapped information.
   * <p>
   * Bytes above that size are always 0.
   *
   * @return size of memory mapped information
   */
  def length(): Long = mappings.last.va.end

  /**
   * Returns a byte array of the specified segment.
   * <p>
   * The distance until-from has to be in Integer range.
   *
   * @param from the virtual start offset
   * @param until the virtual end offset
   * @return byte array containing the elements greater than or equal to index
   * from extending up to (but not including) index until
   */
  def slice(from: Long, until: Long): Array[Byte] = {
    val sliceMappings = mappingsInRange(new VirtRange(from, until))
    val bytes = zeroBytes((until - from).toInt)
    sliceMappings.foreach { m =>
      val start = Math.max(m.va.start, from)
      val end = Math.min(m.va.end, until)
      val mapBytes = readBytesAt(m, start, (end - start).toInt)
      for (i <- 0 until mapBytes.length) {
        val index = (start - from).toInt + i
        bytes(index) = mapBytes(i)
      }
    }
    bytes
  }

  /**
   * Filters all mappings that are relevant for the range.
   */
  private def mappingsInRange(range: VirtRange): List[Mapping] = {
    val (from, until) = range.unpack
    mappings.filter(m => m.va.end > from && m.va.start < until)
  }

  /**
   * Fills an array with 0 bytes of the size
   */
  private def zeroBytes(size: Int): Array[Byte] =
    if (size >= 0) {
      Array.fill(size)(0.toByte)
    } else Array()

  /**
   * Returns the index of the first byte that satisfies the condition.
   *
   * @param p the function that specifies the condition
   * @param from offset to start searching from
   * @return index of the first byte that satisfies the condition
   */
  def indexWhere(p: Byte => Boolean, from: Long): Long = {
    if (from > length) -1
    else {
      val bytes = slice(from, from + chunkSize)
      val index = bytes.indexWhere(p)
      if (index == -1) {
        val nextIndex = this.indexWhere(p, from + chunkSize)
        if (nextIndex == -1) -1 else chunkSize + nextIndex
      } else {
        from + index
      }
    }
  }

  /**
   * Returns the index of the first byte that has the value.
   *
   * @param value value of the byte searched for
   * @param from offset to start searching from
   * @return index of the first byte that has the value
   */
  def indexOf(elem: Byte, from: Long): Long =
    indexWhere(((b: Byte) => b == elem), from)

  /**ByteArrayUtil methods**/

  def getBytesIntValue(offset: Long, length: Int): Int =
    ByteArrayUtil.bytesToInt(this.slice(offset, offset + length))

  def getBytesLongValue(offset: Long, length: Int): Long =
    ByteArrayUtil.bytesToLong(this.slice(offset, offset + length))

}

object MemoryMappedPE {

  /**
   * Simply a range.
   */
  abstract class Range(val start: Long, val end: Long) {
    def unpack(): (Long, Long) = (start, end)
  }

  /**
   * Represents a range of virtual addresses
   */
  class VirtRange(start: Long, end: Long) extends Range(start, end)

  /**
   * Represents a range of physical addresses
   */
  class PhysRange(start: Long, end: Long) extends Range(start, end)

  /**
   * Maps all addresses of a virtual range to all addresses of the physical range.
   * Both ranges have to be of the same size.
   */
  case class Mapping(va: VirtRange, physA: PhysRange) {
    require(va.end - va.start == physA.end - physA.start)
  }

  def main(args: Array[String]): Unit = {
    val file = new File("/home/deque/portextestfiles/testfiles/DLL1.dll")
    val data = PELoader.loadPE(file)
    println(data)
    val loader = new SectionLoader(data)
    val mmpe = apply(data, loader)
    mmpe.slice(31616, 31622).foreach(println)
  }

  def newInstance(data: PEData, secLoader: SectionLoader): MemoryMappedPE =
    apply(data, secLoader)

  /**
   * Creates a representation of the PE content as it is mapped into memory
   */
  def apply(data: PEData, secLoader: SectionLoader): MemoryMappedPE = {
    val mappings = readMemoryMappings(data, secLoader)
    new MemoryMappedPE(mappings, data)
  }

  /**
   * Reads memory mappings for the sections. This shall replace the bytes read
   */
  private def readMemoryMappings(data: PEData, secLoader: SectionLoader): List[Mapping] = {
    val optHeader = data.getOptionalHeader
    if (optHeader.isLowAlignmentMode()) {
      val filesize = data.getFile.length
      List(Mapping(new VirtRange(0, filesize), new PhysRange(0, filesize)))
    } else {
      val table = data.getSectionTable
      val mappings = ListBuffer[Mapping]()
      val maxVA = getMaxVA(table, secLoader)
      for (header <- table.getSectionHeaders().asScala) {
        if (secLoader.isValidSection(header)) {
          val pStart = header.getAlignedPointerToRaw()
          val readSize = secLoader.getReadSize(header)
          val pEnd = pStart + readSize
          val physRange = new PhysRange(pStart, pEnd)
          val vStart = header.getAlignedVirtualAddress()
          val vEnd = vStart + readSize
          val virtRange = new VirtRange(vStart, vEnd)
          mappings += Mapping(virtRange, physRange)
        }
      }
      mappings.toList
    }
  }

  private def getMaxVA(table: SectionTable, secLoader: SectionLoader): Long =
    table.getSectionHeaders().asScala.foldRight(0L) { (header, max) =>
      val headerEnd = header.getAlignedVirtualAddress() + header.getAlignedVirtualSize()
      if (secLoader.isValidSection(header) && headerEnd > max) headerEnd else max
    }

}