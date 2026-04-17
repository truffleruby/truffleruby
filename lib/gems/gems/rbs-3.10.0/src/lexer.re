#include "rbs/lexer.h"

rbs_token_t rbs_lexer_next_token(rbs_lexer_t *lexer) {
  rbs_lexer_t backup;

  backup = *lexer;

  /*!re2c
      re2c:flags:u = 1;
      re2c:api:style = free-form;
      re2c:flags:input = custom;
      re2c:define:YYCTYPE = "unsigned int";
      re2c:define:YYPEEK = "rbs_peek(lexer)";
      re2c:define:YYSKIP = "rbs_skip(lexer);";
      re2c:define:YYBACKUP = "backup = *lexer;";
      re2c:define:YYRESTORE = "*lexer = backup;";
      re2c:yyfill:enable  = 0;

      word = [a-zA-Z0-9_];

      operator = "/" | "~" | "[]=" | "!" | "!=" | "!~" | "-" | "-@" | "+" | "+@"
               | "==" | "===" | "=~" | "<<" | "<=" | "<=>" | ">" | ">=" | ">>" | "%";

      "("   { return rbs_next_token(lexer, pLPAREN); }
      ")"   { return rbs_next_token(lexer, pRPAREN); }
      "["   { return rbs_next_token(lexer, pLBRACKET); }
      "]"   { return rbs_next_token(lexer, pRBRACKET); }
      "{"   { return rbs_next_token(lexer, pLBRACE); }
      "}"   { return rbs_next_token(lexer, pRBRACE); }
      ","   { return rbs_next_token(lexer, pCOMMA); }
      "|"   { return rbs_next_token(lexer, pBAR); }
      "^"   { return rbs_next_token(lexer, pHAT); }
      "&"   { return rbs_next_token(lexer, pAMP); }
      "?"   { return rbs_next_token(lexer, pQUESTION); }
      "*"   { return rbs_next_token(lexer, pSTAR); }
      "**"  { return rbs_next_token(lexer, pSTAR2); }
      "."   { return rbs_next_token(lexer, pDOT); }
      "..." { return rbs_next_token(lexer, pDOT3); }
      "`"   {  return rbs_next_token(lexer, tOPERATOR); }
      "`"   [^ :\x00] [^`\x00]* "`" { return rbs_next_token(lexer, tQIDENT); }
      "->"  { return rbs_next_token(lexer, pARROW); }
      "=>"  { return rbs_next_token(lexer, pFATARROW); }
      "="   { return rbs_next_token(lexer, pEQ); }
      ":"   { return rbs_next_token(lexer, pCOLON); }
      "::"  { return rbs_next_token(lexer, pCOLON2); }
      "<"   { return rbs_next_token(lexer, pLT); }
      "[]"  { return rbs_next_token(lexer, pAREF_OPR); }
      operator  { return rbs_next_token(lexer, tOPERATOR); }

      number = [0-9] [0-9_]*;
      ("-"|"+")? number    { return rbs_next_token(lexer, tINTEGER); }

      "%a{" [^}\x00]* "}"  { return rbs_next_token(lexer, tANNOTATION); }
      "%a(" [^)\x00]* ")"  { return rbs_next_token(lexer, tANNOTATION); }
      "%a[" [^\]\x00]* "]" { return rbs_next_token(lexer, tANNOTATION); }
      "%a|" [^|\x00]* "|"  { return rbs_next_token(lexer, tANNOTATION); }
      "%a<" [^>\x00]* ">"  { return rbs_next_token(lexer, tANNOTATION); }

      "#" (. \ [\x00])*    {
        return rbs_next_token(
          lexer,
          lexer->first_token_of_line ? tLINECOMMENT : tCOMMENT
        );
      }

      "alias"         { return rbs_next_token(lexer, kALIAS); }
      "attr_accessor" { return rbs_next_token(lexer, kATTRACCESSOR); }
      "attr_reader"   { return rbs_next_token(lexer, kATTRREADER); }
      "attr_writer"   { return rbs_next_token(lexer, kATTRWRITER); }
      "bool"          { return rbs_next_token(lexer, kBOOL); }
      "bot"           { return rbs_next_token(lexer, kBOT); }
      "class"         { return rbs_next_token(lexer, kCLASS); }
      "def"           { return rbs_next_token(lexer, kDEF); }
      "end"           { return rbs_next_token(lexer, kEND); }
      "extend"        { return rbs_next_token(lexer, kEXTEND); }
      "false"         { return rbs_next_token(lexer, kFALSE); }
      "in"            { return rbs_next_token(lexer, kIN); }
      "include"       { return rbs_next_token(lexer, kINCLUDE); }
      "instance"      { return rbs_next_token(lexer, kINSTANCE); }
      "interface"     { return rbs_next_token(lexer, kINTERFACE); }
      "module"        { return rbs_next_token(lexer, kMODULE); }
      "nil"           { return rbs_next_token(lexer, kNIL); }
      "out"           { return rbs_next_token(lexer, kOUT); }
      "prepend"       { return rbs_next_token(lexer, kPREPEND); }
      "private"       { return rbs_next_token(lexer, kPRIVATE); }
      "public"        { return rbs_next_token(lexer, kPUBLIC); }
      "self"          { return rbs_next_token(lexer, kSELF); }
      "singleton"     { return rbs_next_token(lexer, kSINGLETON); }
      "top"           { return rbs_next_token(lexer, kTOP); }
      "true"          { return rbs_next_token(lexer, kTRUE); }
      "type"          { return rbs_next_token(lexer, kTYPE); }
      "unchecked"     { return rbs_next_token(lexer, kUNCHECKED); }
      "untyped"       { return rbs_next_token(lexer, kUNTYPED); }
      "void"          { return rbs_next_token(lexer, kVOID); }
      "use"           { return rbs_next_token(lexer, kUSE); }
      "as"            { return rbs_next_token(lexer, kAS); }
      "__todo__"      { return rbs_next_token(lexer, k__TODO__); }

      unicode_char = "\\u" [0-9a-fA-F]{4};
      oct_char = "\\x" [0-9a-f]{1,2};
      hex_char = "\\" [0-7]{1,3};

      dqstring = ["] (unicode_char | oct_char | hex_char | "\\" [^xu] | [^\\"\x00])* ["];
      sqstring = ['] ("\\"['\\] | [^'\x00])* ['];

      dqstring     { return rbs_next_token(lexer, tDQSTRING); }
      sqstring     { return rbs_next_token(lexer, tSQSTRING); }
      ":" dqstring { return rbs_next_token(lexer, tDQSYMBOL); }
      ":" sqstring { return rbs_next_token(lexer, tSQSYMBOL); }

      identifier = [a-zA-Z_] word* [!?=]?;
      symbol_opr = ":|" | ":&" | ":/" | ":%" | ":~" | ":`" | ":^"
                 | ":==" | ":=~" | ":===" | ":!" | ":!=" | ":!~"
                 | ":<" | ":<=" | ":<<" | ":<=>" | ":>" | ":>=" | ":>>"
                 | ":-" | ":-@" | ":+" | ":+@" | ":*" | ":**" | ":[]" | ":[]=";

      global_ident = [0-9]+
                   | "-" [a-zA-Z0-9_]
                   | [~*$?!@\\/;,.=:<>"&'`+]
                   | [^ \t\r\n:;=.,!"$%&()-+~|\\'[\]{}*/<>^\x00]+;

      ":" identifier     { return rbs_next_token(lexer, tSYMBOL); }
      ":@" identifier    { return rbs_next_token(lexer, tSYMBOL); }
      ":@@" identifier   { return rbs_next_token(lexer, tSYMBOL); }
      ":$" global_ident  { return rbs_next_token(lexer, tSYMBOL); }
      symbol_opr         { return rbs_next_token(lexer, tSYMBOL); }

      [a-z] word*           { return rbs_next_token(lexer, tLIDENT); }
      [A-Z] word*           { return rbs_next_token(lexer, tUIDENT); }
      "_" [a-z0-9_] word*   { return rbs_next_token(lexer, tULLIDENT); }
      "_" [A-Z] word*       { return rbs_next_token(lexer, tULIDENT); }
      "_"                   { return rbs_next_token(lexer, tULLIDENT); }
      [a-zA-Z_] word* "!"   { return rbs_next_token(lexer, tBANGIDENT); }
      [a-zA-Z_] word* "="   { return rbs_next_token(lexer, tEQIDENT); }

      "@" [a-zA-Z_] word*   { return rbs_next_token(lexer, tAIDENT); }
      "@@" [a-zA-Z_] word*  { return rbs_next_token(lexer, tA2IDENT); }

      "$" global_ident      { return rbs_next_token(lexer, tGIDENT); }

      skip = ([ \t]+|[\r\n]);

      skip     { return rbs_next_token(lexer, tTRIVIA); }
      "\x00"   { return rbs_next_eof_token(lexer); }
      *        { return rbs_next_token(lexer, ErrorToken); }
  */
}
