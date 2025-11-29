# Ruby 3.4 removed the deprecated `SPELL_CHECKERS`. The const is referred to in Thor <= 1.2.1.
DidYouMean.const_set(:SPELL_CHECKERS, DidYouMean::SpellChecker)