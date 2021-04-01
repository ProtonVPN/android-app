//    OpenVPN -- An application to securely tunnel IP networks
//               over a single port, with support for SSL/TLS-based
//               session authentication and key exchange,
//               packet encryption, packet authentication, and
//               packet compression.
//
//    Copyright (C) 2012-2020 OpenVPN Inc.
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU Affero General Public License Version 3
//    as published by the Free Software Foundation.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//    GNU Affero General Public License for more details.
//
//    You should have received a copy of the GNU Affero General Public License
//    along with this program in the COPYING file.
//    If not, see <http://www.gnu.org/licenses/>.

// Native companion code for JellyBeanHack.java

#include <stdio.h>
#include <dlfcn.h>
#include <jni.h>

#include <android/log.h>

#ifdef SWIGEXPORT
#define EXPORT SWIGEXPORT
#else
#define EXPORT
#endif

#ifndef OPENVPN_PACKAGE_ID
#error OPENVPN_PACKAGE_ID must be defined
#endif

#define MAKE_SYM2(pkg_id, suffix) Java_ ## pkg_id ## _JellyBeanHack_ ## suffix
#define MAKE_SYM(pkg_id, suffix) MAKE_SYM2(pkg_id, suffix)

#define RSA_SIGN_INIT MAKE_SYM(OPENVPN_PACKAGE_ID, rsa_1sign_1init)
#define RSA_SIGN      MAKE_SYM(OPENVPN_PACKAGE_ID, rsa_1sign)
#define PKEY_RETAIN   MAKE_SYM(OPENVPN_PACKAGE_ID, pkey_1retain)

extern "C" {
  jint RSA_SIGN_INIT(JNIEnv* env, jclass);
  jbyteArray RSA_SIGN(JNIEnv* env, jclass, jbyteArray from, jint pkeyRef);
  void PKEY_RETAIN(JNIEnv* env, jclass, jint pkeyRef);
};

typedef void *RSA;

enum {
  NID_md5_sha1=114,
  CRYPTO_LOCK_EVP_PKEY=10,
};

struct EVP_PKEY
{
  int type;
  int save_type;
  int references;
  void *ameth;
  void *engine;
  union {
    RSA *rsa;
  } pkey;
};

typedef int (*RSA_size_func_t)(const RSA *);

typedef int (*RSA_sign_func_t)(int type, const unsigned char *m, unsigned int m_length,
			       unsigned char *sigret, unsigned int *siglen, RSA *rsa);

typedef void (*ERR_print_errors_fp_func_t)(FILE *fp);

typedef int (*CRYPTO_add_lock_func_t)(int *pointer, int amount, int type, const char *file, int line);

static bool initialized;
static RSA_size_func_t RSA_size;
static RSA_sign_func_t RSA_sign;
static ERR_print_errors_fp_func_t ERR_print_errors_fp;
static CRYPTO_add_lock_func_t CRYPTO_add_lock;

inline bool callbacks_defined()
{
  return RSA_size != NULL
    && RSA_sign != NULL
    && ERR_print_errors_fp != NULL
    && CRYPTO_add_lock != NULL;
}

EXPORT jint RSA_SIGN_INIT(JNIEnv* env, jclass)
{
  if (!initialized)
    {
      void *handle = dlopen("libcrypto.so", RTLD_NOW);
      if (handle)
	{
	  RSA_size =  (RSA_size_func_t) dlsym(handle, "RSA_size");
	  RSA_sign =  (RSA_sign_func_t) dlsym(handle, "RSA_sign");
	  ERR_print_errors_fp = (ERR_print_errors_fp_func_t) dlsym(handle, "ERR_print_errors_fp");
	  CRYPTO_add_lock =  (CRYPTO_add_lock_func_t) dlsym(handle, "CRYPTO_add_lock");
	}
      initialized = true;
    }
  return callbacks_defined();
}

static int jni_throw(JNIEnv* env, const char* className, const char* msg)
{
  jclass exceptionClass = env->FindClass(className);

  if (exceptionClass == NULL) {
    // ClassNotFoundException now pending
    return -1;
  }

  if (env->ThrowNew( exceptionClass, msg) != JNI_OK) {
    // an exception, most likely OOM, will now be pending
    return -1;
  }

  env->DeleteLocalRef(exceptionClass);
  return 0;
}

EXPORT jbyteArray RSA_SIGN(JNIEnv* env, jclass, jbyteArray from, jint pkeyRef)
{
  if (!callbacks_defined())
    {
      jni_throw(env, "java/lang/NullPointerException", "rsa_sign: OpenSSL callbacks undefined");
      return NULL;
    }

  EVP_PKEY* pkey = reinterpret_cast<EVP_PKEY*>(pkeyRef);
  if (pkey == NULL || from == NULL)
    {
      jni_throw(env, "java/lang/NullPointerException", "rsa_sign: from/pkey is NULL");
      return NULL;
    }

  jbyte* data =  env->GetByteArrayElements(from, NULL);
  if (data == NULL)
    {
      jni_throw(env, "java/lang/NullPointerException", "rsa_sign: data is NULL");
      return NULL;
    }
  int datalen = env->GetArrayLength(from);

  unsigned int siglen;
  unsigned char* sigret = new unsigned char[(*RSA_size)(pkey->pkey.rsa)];

  if ((*RSA_sign)(NID_md5_sha1, (unsigned char*) data, datalen,
		  sigret, &siglen, pkey->pkey.rsa) <= 0)
    {
      jni_throw(env, "java/security/InvalidKeyException", "OpenSSL RSA_sign failed");
      (*ERR_print_errors_fp)(stderr);
      return NULL;
    }

  jbyteArray jb = env->NewByteArray(siglen);
  env->SetByteArrayRegion(jb, 0, siglen, (jbyte *)sigret);
  delete [] sigret;
  return jb;
}

EXPORT void PKEY_RETAIN(JNIEnv* env, jclass, jint pkeyRef)
{
  EVP_PKEY* pkey = reinterpret_cast<EVP_PKEY*>(pkeyRef);
  if (pkey && CRYPTO_add_lock)
    {
      const int newref = (*CRYPTO_add_lock)(&pkey->references, 1, CRYPTO_LOCK_EVP_PKEY, __FILE__, __LINE__);
      __android_log_print(ANDROID_LOG_DEBUG, "openvpn", "pkey_retain ref=%d", newref);
    }
}
