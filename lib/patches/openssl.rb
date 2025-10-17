# frozen_string_literal: true
# truffleruby_primitives: true

# Set SSL_CERT_DIR and SSL_CERT_FILE
# This needs to be dynamic because e.g. there is no standard path on Linux for the cert file.
# Based on https://github.com/rubygems/rubygems/issues/2415#issuecomment-509806259
# and https://github.com/rbenv/ruby-build/blob/da5bb283df/bin/ruby-build#L1272 
# We could try to symlink in src/main/c/libssl/ssl but it seems much simpler to just use the env vars.

unless ENV['SSL_CERT_FILE'] || ENV['SSL_CERT_DIR'] # If already set, just use that
  certs_file = nil
  certs_dir = nil

  if Truffle::Platform.linux?
    if File.exist? '/etc/pki/tls/cert.pem' # Fedora-based
      certs_file = '/etc/pki/tls/cert.pem'
      certs_dir  = '/etc/pki/tls/certs'
    elsif File.exist? '/etc/ssl/certs/ca-certificates.crt' # Debian-based
      certs_file = '/etc/ssl/certs/ca-certificates.crt'
      certs_dir  = '/etc/ssl/certs'
    end
  elsif Truffle::Platform.darwin?
    search_homebrew = -> homebrew {
      if cert = "#{homebrew}/opt/ca-certificates/share/ca-certificates/cacert.pem" and File.exist?(cert)
        cert
      end
    }

    default_homebrew_prefix = Truffle::Platform.aarch64? ? '/opt/homebrew' : '/usr/local'
    if cert = search_homebrew.call(default_homebrew_prefix)
      certs_file = cert
    else
      homebrew = `brew --prefix 2>/dev/null`.strip
      homebrew = nil unless $?.success? and !homebrew.empty? and Dir.exist?(homebrew)
      if homebrew and cert = search_homebrew.call(homebrew)
        certs_file = cert
      elsif File.exist?('/opt/local/share/curl/curl-ca-bundle.crt') # MacPorts
        certs_file = '/opt/local/share/curl/curl-ca-bundle.crt'
      end
    end
  end

  if certs_file || certs_dir
    Truffle::Debug.log_config("Found CA certificates: #{certs_file} #{certs_dir}")
    ENV['SSL_CERT_FILE'] = certs_file
    ENV['SSL_CERT_DIR'] = certs_dir
  else
    warn <<~WARN
      WARNING: Could not find CA certificates on this system, the OpenSSL gem will not work properly.
      WARNING: Please install CA certificates, the package is named ca-certificates for many package managers (including dnf, apt and brew).
    WARN
  end
end

require Primitive.get_original_require(__FILE__)
