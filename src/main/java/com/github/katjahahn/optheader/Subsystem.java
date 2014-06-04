package com.github.katjahahn.optheader;

import com.github.katjahahn.Characteristic;

public enum Subsystem implements Characteristic {
	IMAGE_SYSTEM_UNKNOWN,
	IMAGE_SUBSYSTEM_NATIVE,
	IMAGE_SUBSYSTEM_WINDOWS_GUI,
	IMAGE_SUBSYSTEM_WINDOWS_CUI,
	IMAGE_SUBSYSTEM_POSIX_CUI,
	IMAGE_SUBSYSTEM_WINDOWS_CE_GUI,
	IMAGE_SUBSYSTEM_EFI_APPLICATION,
	IMAGE_SUBSYSTEM_EFI_BOOT_SERVICE_DRIVER,
	IMAGE_SUBSYSTEM_EFI_RUNTIME_DRIVER,
	IMAGE_SUBSYSTEM_EFI_ROM,
	IMAGE_SUBSYSTEM_XBOX;

	@Override
	public boolean isReserved() {
		return false;
	}

	@Override
	public boolean isDeprecated() {
		return false;
	}
}