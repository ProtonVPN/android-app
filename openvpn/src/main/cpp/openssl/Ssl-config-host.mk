# Auto-generated - DO NOT EDIT!
#
# This script will append to the following variables:
#
#    LOCAL_CFLAGS
#    LOCAL_C_INCLUDES
#    LOCAL_SRC_FILES_$(TARGET_ARCH)
#    LOCAL_SRC_FILES_$(TARGET_2ND_ARCH)
#    LOCAL_CFLAGS_$(TARGET_ARCH)
#    LOCAL_CFLAGS_$(TARGET_2ND_ARCH)
#    LOCAL_ADDITIONAL_DEPENDENCIES
#    LOCAL_EXPORT_C_INCLUDE_DIRS


LOCAL_ADDITIONAL_DEPENDENCIES += $(LOCAL_PATH)/Ssl-config-host.mk

common_cflags :=

common_src_files := \
  ssl/bio_ssl.c \
  ssl/d1_lib.c \
  ssl/d1_msg.c \
  ssl/d1_srtp.c \
  ssl/methods.c \
  ssl/pqueue.c \
  ssl/record/dtls1_bitmap.c \
  ssl/record/rec_layer_d1.c \
  ssl/record/rec_layer_s3.c \
  ssl/record/ssl3_buffer.c \
  ssl/record/ssl3_record.c \
  ssl/s3_cbc.c \
  ssl/s3_enc.c \
  ssl/s3_lib.c \
  ssl/s3_msg.c \
  ssl/ssl_asn1.c \
  ssl/ssl_cert.c \
  ssl/ssl_ciph.c \
  ssl/ssl_conf.c \
  ssl/ssl_err.c \
  ssl/ssl_init.c \
  ssl/ssl_lib.c \
  ssl/ssl_mcnf.c \
  ssl/ssl_rsa.c \
  ssl/ssl_sess.c \
  ssl/ssl_stat.c \
  ssl/ssl_txt.c \
  ssl/ssl_utst.c \
  ssl/statem/statem.c \
  ssl/statem/statem_clnt.c \
  ssl/statem/statem_dtls.c \
  ssl/statem/statem_lib.c \
  ssl/statem/statem_srvr.c \
  ssl/t1_enc.c \
  ssl/t1_ext.c \
  ssl/t1_lib.c \
  ssl/t1_reneg.c \
  ssl/t1_trce.c \
  ssl/tls_srp.c \

common_c_includes := \
  openssl/. \
  openssl/crypto \
  openssl/include \

arm_clang_asflags :=

arm_cflags :=

arm_src_files :=

arm_exclude_files :=

arm64_clang_asflags :=

arm64_cflags :=

arm64_src_files :=

arm64_exclude_files :=

x86_clang_asflags :=

x86_cflags :=

x86_src_files :=

x86_exclude_files :=

x86_64_clang_asflags :=

x86_64_cflags :=

x86_64_src_files :=

x86_64_exclude_files :=


#LOCAL_LDLIBS :=  -latomic
LOCAL_EXPORT_C_INCLUDE_DIRS := $(LOCAL_PATH)/include

LOCAL_CFLAGS += $(common_cflags)
LOCAL_C_INCLUDES += $(common_c_includes) $(local_c_includes)

ifeq ($(HOST_OS),linux)
LOCAL_CFLAGS_x86 += $(x86_cflags)
LOCAL_SRC_FILES_x86 += $(filter-out $(x86_exclude_files), $(common_src_files) $(x86_src_files))
LOCAL_CFLAGS_x86_64 += $(x86_64_cflags)
LOCAL_SRC_FILES_x86_64 += $(filter-out $(x86_64_exclude_files), $(common_src_files) $(x86_64_src_files))
else
$(warning Unknown host OS $(HOST_OS))
LOCAL_SRC_FILES += $(common_src_files)
endif
