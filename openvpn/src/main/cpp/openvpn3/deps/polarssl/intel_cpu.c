#if defined(_WIN32)
typedef unsigned __int64 IA32CAP;
#else
typedef unsigned long long IA32CAP;
#endif

IA32CAP OPENSSL_ia32_cpuid(void);

unsigned int OPENSSL_ia32cap_P[2]; // GLOBAL

void OPENSSL_cpuid_setup(void)
{
  const IA32CAP vec = OPENSSL_ia32_cpuid();

  /*
   * |(1<<10) sets a reserved bit to signal that variable
   * was initialized already... This is to avoid interference
   * with cpuid snippets in ELF .init segment.
   */
  OPENSSL_ia32cap_P[0] = (unsigned int)vec|(1<<10);
  OPENSSL_ia32cap_P[1] = (unsigned int)(vec>>32);
}
