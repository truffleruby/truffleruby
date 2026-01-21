This directory contains headers which declare non-public functions that are used in gems.
They contain only the minimal subset necessary from the corresponding CRuby internal header.

This directory is only on include path when $extmk is true, that is for core C extensions and MRI tests.
