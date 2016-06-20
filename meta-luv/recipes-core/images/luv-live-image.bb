LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/LICENSE;md5=4d92cd373abda3937c2bc47fbc49d690"

DEPENDS_${PN} = "grub-efi bits"

HDDDIR = "${S}/hddimg"
HDDIMG_ID = "423CC2C8"
LABELS = "luv"

INITRD_IMAGE = "core-image-efi-initramfs"
INITRD = "${DEPLOY_DIR_IMAGE}/${INITRD_IMAGE}-${MACHINE}.cpio.gz"
MACHINE_FEATURES += "efi"
APPEND = "debug crashkernel=256M console=ttyS0,115200 console=ttyPCH0,115200 ip=dhcp log_buf_len=1M"
APPEND_netconsole = "luv_netconsole=10.11.12.13,64001"
APPEND_aarch64 = "crashkernel=256M console=ttyAMA0 uefi_debug acpi=force"

SPLASH_IMAGE = "blue-luv.jpg"

GRUB_TIMEOUT = "2"

inherit bootimg

SRC_URI = "file://blue-luv.jpg"

S = "${WORKDIR}"

build_img() {
    IMG="${DEPLOY_DIR_IMAGE}/${PN}.img"
    VFAT="${DEPLOY_DIR_IMAGE}/${IMAGE_LINK_NAME}.hddimg"

    # Parameters of the vfat partition for test results
    # Sectors: 512 bytes
    # Blocks: 1024 bytes
    VFAT_RESULTS=${DEPLOY_DIR_IMAGE}/${PN}-results.hddimg
    # 16MB of space for test results
    VFAT_RESULTS_SPACE=16777216
    VFAT_RESULTS_BLOCKS=$(expr $VFAT_RESULTS_SPACE / 1024)
    # TODO: do we need to dynamically generate the UUID?
    # For now, every time this UUID changes, the file etc/init.d/luv-test-manager
    # needs to be updated accordingly.
    VFAT_RESULTS_UUID=05D61523
    VFAT_RESULTS_LABEL="luv-results"
    FATSIZE="-F32"
    BLKSIZE=512
    BYTES_PER_SCTRS=512
    SECTOR_ALIGN=3145728

    if [ -e ${VFAT_RESULTS} ]; then
        rm ${VFAT_RESULTS}
    fi

    mkdosfs ${FATSIZE} -C ${VFAT_RESULTS} -S ${BLKSIZE} -i ${VFAT_RESULTS_UUID} \
            -n ${VFAT_RESULTS_LABEL} ${VFAT_RESULTS_BLOCKS}

    dd if=/dev/zero of=$IMG bs=${BLKSIZE} count=1

    VFAT_SIZE=$(du -L --apparent-size -bs $VFAT | cut -f 1)
    VFAT_RESULTS_SIZE=$(du -L --apparent-size -bs $VFAT_RESULTS | cut -f 1)

    IMG_SIZE=$(expr $VFAT_SIZE + $VFAT_RESULTS_SIZE + $SECTOR_ALIGN)

    # Convert partitions from bytes to sectors and set start/end for partitions
    START_P1=2048
    END_P1=$(expr $(expr $VFAT_RESULTS_SIZE / $BYTES_PER_SCTRS) + $START_P1)

    # Start second partition on the first sector after the first partition

    START_P2=$(expr $END_P1 + $START_P1)
    END_P2=$(expr $(expr $VFAT_SIZE / $BYTES_PER_SCTRS) + $START_P2)

    dd if=/dev/zero of=$IMG bs=1 seek=$IMG_SIZE count=0
    parted $IMG mklabel gpt

    # First partition for luv-results folder
    parted $IMG unit s mkpart primary fat32 ${START_P1} ${END_P1}

    # Second partition for boot (luv) partition
    parted $IMG unit s mkpart ESP fat32 ${START_P2} ${END_P2}

    parted $IMG set 2 boot on

    dd conv=notrunc if=${VFAT_RESULTS} of=$IMG seek=${START_P1} bs=${BLKSIZE}

    dd conv=notrunc if=${VFAT} of=$IMG seek=${START_P2} bs=${BLKSIZE}


}

python do_create_img() {
    bb.build.exec_func('build_img', d)
}

do_bootimg[depends] += "${INITRD_IMAGE}:do_build"
do_bootimg[depends] += "virtual/kernel:do_populate_sysroot"
do_bootimg[depends] += "shim-signed:do_deploy"

addtask create_img after do_bootimg before do_build
addtask do_unpack before do_build
