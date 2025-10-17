require 'mkmf'

dir_config("openssl")

have_library 'ssl'
have_library 'crypto'

create_makefile('xopenssl')
