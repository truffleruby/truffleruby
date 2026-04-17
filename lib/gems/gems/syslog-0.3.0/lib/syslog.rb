begin
  require 'syslog_ext'
rescue LoadError
  raise LoadError.new(<<-EOS)
    Can't load Syslog!

    Syslog is not supported on your system. For Windows
    we recommend using the win32-eventlog gem.
  EOS
end
