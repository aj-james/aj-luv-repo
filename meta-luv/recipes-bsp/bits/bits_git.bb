SUMMARY = "BIOSBits is a test suite that runs UEFI BIOS tests."

DESCRIPTION = " The Intel BIOS Implementation Test Suite (BITS) provides a bootable \
pre-OS environment for testing BIOSes and in particular their initialization \
of Intel processors, hardware, and technologies. BITS can verify your BIOS \
against many Intel recommendations. In addition, BITS includes Intel's \
official reference code as provided to BIOS, which you can use to override \
your BIOS's hardware initialization with a known-good configuration, and then boot an OS."

# Home Page
HOMEPAGE = "http://biosbits.org/"

#License
LICENSE = "GPLv3"
LIC_FILES_CHKSUM = "file://COPYING;md5=71a9ec458a3c65c2bfb461b227ef3049"

BBCLASSEXTEND = "native"

BITSVERSION="2005"
PV="${BITSVERSION}+git${SRCPV}"

inherit deploy
inherit luv-test

# TODO: add patch for a CPIO log file
SRCREV ="6026f6460fee9dff3897191a2b972c9536f23e53"
SRC_URI = "gitsm://github.com/biosbits/bits.git;protocol=http  \
           file://GRUB-core-lib-crypto-complete-handling-fgets-return-.patch \
           file://GRUB-util-of-complete-handling-of-read-return-values.patch \
           file://GRUB-core-net-bootp-conserve-variable-qualifier.patch; \
           file://BITS-rc-python-Use-fwrap-to-build-python.patch \
           file://BITS-GRUB-adjust-configure-params-for-bitbake.patch \
           file://BITS-Makefile-split-grub-build-from-image-generation.patch \
           file://BITS-Makefile-do-not-depend-bytecompile-on-build-pyt.patch \
           file://BITS-Makefile-allow-incremental-builds.patch \
           file://BITS-bits-cfg.txt-Set-batch-mode.patch \
           file://BITS-init.cfg-Exit-BITS-upon-completion.patch \
           file://luv-test-bits \
           file://luv-parser-bits \
           file://0001-only-output-to-log.patch;apply=no \
          "

S = "${WORKDIR}/git"

DEPENDS = "virtual/gettext autogen-native gettext-native sqlite3-native zip-native \
           xorriso-native bits-native bits-python-native"

COMPATIBLE_HOST = '(x86_64.*|i.86.*)-(linux|freebsd.*)'

# Determine the target arch for the bios modules before the native class
# clobbers TARGET_ARCH.

def get_bits_arch(d):
    import re
    target = d.getVar('TARGET_ARCH', True)
    if target == "x86_64":
        return 'x86_64'
    elif re.match('i.86', target):
        return 'i386'
    else:
        raise bb.parse.SkipPackage("TARGET_ARCH %s not supported!" % target)

BITS_ARCH = "${@get_bits_arch(d)}"

LUV_TEST_LOG_PARSER = "luv-parser-bits"

do_deploy() {

       install -d ${DEPLOYDIR}/bits

       cp -r ${B}/build/bits-${BITSVERSION}/boot/ ${DEPLOYDIR}/bits/
       cp -r ${B}/build/bits-${BITSVERSION}/efi ${DEPLOYDIR}/bits/
}


addtask deploy before do_build after do_install

do_compile() {
	sed -i s/BUILD_SYS/${BUILD_SYS}/ Makefile
	sed -i s/TARGET_SYS/${TARGET_SYS}/ Makefile
	sed -i s/HOST_SYS/${HOST_SYS}/ Makefile

	oe_runmake bytecompile-pylib
	oe_runmake bytecompile-bits-python
	oe_runmake build-grub-${BITS_ARCH}-efi
}


BITS_INSTALL_TARGETS ="install-doc install-grub-cfg install-log install-bitsversion \
                       install-bitsconfigdefaults install-toplevel-cfg install-bits-cfg \
                       install-readme install-news install-install install-copying"

do_install() {
	oe_runmake mkimage-grub-${BITS_ARCH}-efi
	oe_runmake ${BITS_INSTALL_TARGETS}

	# TODO: Do we need to distribute source code?
	# This target creates a tar ball of the source code. This directory can change
	# while this happens as other targets are built in parallel (and create their
	# own targets). Thus,we need to run this target separately.
	oe_runmake install-src-bits

	install -d ${D}${bindir}
	install -m 0755 ${WORKDIR}/luv-test-bits ${D}/${bindir}/bits
}

do_install_class-native() {
        install -d ${D}${bindir}
        install -m 755 ${S}/build/grub-inst/bin/grub-mkimage ${D}${bindir}/bits-grub-mkimage
}

