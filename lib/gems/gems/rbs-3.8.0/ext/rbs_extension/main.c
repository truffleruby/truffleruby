#include "rbs_extension.h"

void
Init_rbs_extension(void)
{
#ifdef HAVE_RB_EXT_RACTOR_SAFE
  rb_ext_ractor_safe(true);
#endif  
  rbs__init_constants();
  rbs__init_location();
  rbs__init_parser();
}
