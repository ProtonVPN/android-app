/* Functions to help coverity do static analysis on cmocka */
void exit_test(const int quit_application)
{
      __coverity_panic__();
}
