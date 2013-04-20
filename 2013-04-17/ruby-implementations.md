# Ruby Implementierungen
## Es gibt mehr als die von Matz...

# Ruby Implementierungen
* Was gibt's denn so an Möglichkeiten um Ruby zu nutzen?
* Überblick über die verschiedenen Ruby Implementierungen
* Code Beispiel, wie die Funktion String#reverse implementiert ist

* Ich hätte euch gerne RubySpec Resultate gezeigt, allerdings ist die Ausführung auf Travis mehr oder weniger problematisch

> @klaustopher the point to make is that RubySpec covers tons and tons of Ruby behavior and being compatible is very hard.


> @klaustopher also note that since MRI often neither documents nor tests behavior, discovering it is hard, and most impl miss a lot.


quotes by [@brixen](http://www.twitter.com/brixen)

## [MRI](http://ruby-lang.org) (Matz' Ruby Interpreter)
* auch: CRuby
* Referenzimplementierung
* bis 2011 de-facto Standard für Ruby
* Komplett zur Laufzeit interpretiert
* bis Ruby 1.8.7
* GIL, dadurch nur ein Thread gleichzeitig. Ausnahme: IO
* C Extensions können direkt in den Ruby Interpreter eingreifen

```c
static VALUE rb_str_reverse(str) VALUE str;
{
  VALUE obj;
  char *s, *e, *p;

  if (RSTRING(str)->len <= 1) return rb_str_dup(str);

  obj = rb_str_new5(str, 0, RSTRING(str)->len);
  s = RSTRING(str)->ptr; e = s + RSTRING(str)->len - 1;
  p = RSTRING(obj)->ptr;

  while (e >= s) {
    *p++ = *e--;
  }
  OBJ_INFECT(obj, str);

  return obj;
}
```

## [YARV](http://ruby-lang.org)  (Yet another Ruby VM)
* auch: KRI (Koicihi's Ruby Interpreter)
* Seit 1.1.2007 Teil des Ruby Repos
* Seit 26.12.2007 (Ruby 1.9.0) offizielle Ruby Implementierung
* VM basiert, JIT Compiler
* Encoding Support durch [Oniguruma](http://www.geocities.jp/kosako3/oniguruma)
* GVL, dadurch nur ein Thread gleichzeitig. Ausnahme: IO

```c
static VALUE rb_str_reverse(VALUE str)
{
  rb_encoding *enc;
  VALUE rev;
  char *s, *e, *p;
  int single = 1;

  if (RSTRING_LEN(str) <= 1) return rb_str_dup(str);
  enc = STR_ENC_GET(str);
  rev = rb_str_new5(str, 0, RSTRING_LEN(str));
  s = RSTRING_PTR(str); e = RSTRING_END(str);
  p = RSTRING_END(rev);

  if (RSTRING_LEN(str) > 1) {
    if (single_byte_optimizable(str)) {
      while (s < e) {
        *--p = *s++;
      }
    }
    else if (ENC_CODERANGE(str) == ENC_CODERANGE_VALID) {
      while (s < e) {
        int clen = rb_enc_fast_mbclen(s, e, enc);

        if (clen > 1 || (*s & 0x80)) single = 0;
        p -= clen;
        memcpy(p, s, clen);
        s += clen;
      }
    }
    else {
      while (s < e) {
        int clen = rb_enc_mbclen(s, e, enc);

        if (clen > 1 || (*s & 0x80)) single = 0;
        p -= clen;
        memcpy(p, s, clen);
        s += clen;
      }
    }
  }
  STR_SET_LEN(rev, RSTRING_LEN(str));
  OBJ_INFECT(rev, str);
  if (ENC_CODERANGE(str) == ENC_CODERANGE_UNKNOWN) {
    if (single) {
      ENC_CODERANGE_SET(str, ENC_CODERANGE_7BIT);
    }
    else {
      ENC_CODERANGE_SET(str, ENC_CODERANGE_VALID);
    }
  }
  rb_enc_cr_str_copy_for_substr(rev, str);

  return rev;
}

```

## [ree](http://www.rubyenterpriseedition.com/) (Ruby Enterprise Edition)
* Kompatibel mit 1.8.7
* Letzter Commit: Februar 2012
* Entwickelt von [Phusion](http://phusion.nl) um Rails Performance zu verbessern
* copy-on-write freundlicher Garbage Collector, ca 33% weniger Speicherverbrauch
* [tcmalloc](https://code.google.com/p/gperftools/) (Multi Threaded malloc)

```
static VALUE rb_str_reverse(str) VALUE str;
{
  VALUE obj;
  char *s, *e, *p;

  if (RSTRING(str)->len <= 1) return rb_str_dup(str);

  obj = rb_str_new5(str, 0, RSTRING(str)->len);
  s = RSTRING(str)->ptr; e = s + RSTRING(str)->len - 1;
  p = RSTRING(obj)->ptr;

  while (e >= s) {
    *p++ = *e--;
  }
  OBJ_INFECT(obj, str);

  return obj;
}
```

## [Rubinius](http://rubini.us)
* Ruby Implementierung in Ruby
* Virtuelle Bytecode Maschine in C++ (sehr leichtgewichtig, 123 Instruktionen, 256 in der JVM), JIT Compiler
* Aktuelle Version 1.2.4 implementiert Ruby 1.8.7, 2.0.0.rc1 implementiert Ruby 1.9.3 und Teile von Ruby 2.0
* Rubinius Compiler kann aus Ruby aufgerufen werden. Dadurch Zugriff auf [AST](http://rubini.us/doc/en/bytecode-compiler/ast/), etc.
`"a = 'Hallo Cologne.rb'".to_sexp => [:lasgn, :a, [:lit, "Hallo Cologne.rb"]]`
* Echtes Multithreading
* Keine C Extensions, [FFI Gem](https://github.com/ffi/ffi)

```ruby
def reverse
  dup.reverse!
end

def reverse!
  return self if @num_bytes <= 1
  self.modify!

  @data.reverse(0, @num_bytes) # <== Byte Array
  self
end
```

```cpp
Object* Tuple::reverse(STATE, Fixnum* o_start, Fixnum* o_total) {
  native_int start = o_start->to_native();
  native_int total = o_total->to_native();

  if(total <= 0 || start < 0 || start >= num_fields()) return this;

  native_int end = start + total - 1;
  if(end >= num_fields()) end = num_fields() - 1;

  Object** pos1 = field + start;
  Object** pos2 = field + end;

  register Object* tmp;
  while(pos1 < pos2) {
    tmp = *pos1;
    *pos1++ = *pos2;
    *pos2-- = tmp;
  }

  return this;
}
```

## [JRuby](http://www.jruby.org)
* Ruby Implementation **auf** der JVM, JIT Compiler oder `jrubyc`
* Interaktion mit anderen Komponenten der JVM Möglich (Java Standard Lib)
* Kompatibel mit MRI seit 1.4
    * JRuby 1.4.0 => Ruby 1.8.7
    * JRuby 1.6.0 => Ruby 1.9.2
    * JRuby 1.7.0 => Ruby 1.9.3
* Seit 1.7.0: [Invoke Dynamic (InDy)](http://www.youtube.com/watch?v=bw-NBcFKNtc) - JVM (seit Java 7 experimentell) kann dynamische Sprachen lernen. 
* Darauf aufbauend: [Ruboto](http://ruboto.org), [rubyflux](https://github.com/headius/rubyflux), [TorqueBox](http://torquebox.org)
* Echtes Multithreading
* Seit 1.7 keine C Extensions ([Java Native Interface](http://docs.oracle.com/javase/7/docs/technotes/guides/jni/)) mehr supported, FFI Gem

```java
@JRubyMethod(name = "reverse", compat = RUBY1_8)
public IRubyObject reverse(ThreadContext context) {
    Ruby runtime = context.runtime;
    if (value.getRealSize() <= 1) return strDup(context.runtime);

    byte[]bytes = value.getUnsafeBytes();
    int p = value.getBegin();
    int len = value.getRealSize();
    byte[]obytes = new byte[len];

    for (int i = 0; i <= len >> 1; i++) {
        obytes[i] = bytes[p + len - i - 1];
        obytes[len - i - 1] = bytes[p + i];
    }

    return new RubyString(runtime, getMetaClass(), new ByteList(obytes, false)).infectBy(this);
}
```

## [MacRuby](http://macruby.org) / [RubyMotion](http://rubymotion.com)
* Ruby 1.9 Implementierung auf der Objective-C Runtime
* HotCocoa zum Erstellen von Cocoa Applikationen
* Integration in [XCode](https://github.com/MacRuby/MacRuby/wiki/Creating-a-simple-application) und da ObjC Bytecode entsteht ==> AppStore
* 2012 Fork zu RubyMotion

```c
static VALUE rstr_reverse(VALUE str, SEL sel)
{
    VALUE obj = rb_str_new3(str);
    rstr_reverse_bang(obj, 0); // Methode entscheidet je nach Charset, welche Funktion aufgerufen wird
    return obj;
}

static void rstr_reverse_bang_uchar32(VALUE str)
{
    char *new_bytes = xmalloc(RSTR(str)->length_in_bytes);
    __block long pos = RSTR(str)->length_in_bytes;
    str_each_uchar32(RSTR(str), ^(UChar32 c, long start_index, long char_len, bool *stop) {
  pos -= char_len;
	memcpy(&new_bytes[pos], &RSTR(str)->bytes[start_index], char_len);
    });
    assert(pos == 0);

    RSTR(str)->capacity_in_bytes = RSTR(str)->length_in_bytes;
    GC_WB(&RSTR(str)->bytes, new_bytes);
}
```

## [Topaz](https://topaz.readthedocs.org)
* Ruby 1.9.3 kompatibel
* Ruby VM implementiert in RPython (Subset von Python, Extrahiert aus PyPy)
* Keine Python <=> Ruby Bridge
* Demonstration, dass sich RPython sehr gut eignet um VMs zu bauen
* Python Garbage Collector, PyPy JIT
* Nur ein Bruchteil von Ruby implementiert. Erst das was nötig ist um MSpec zu starten
* Keine C Extensions (FFI Support ist geplant)

```python
@classdef.method("reverse!")
@check_frozen()
def method_reverse_i(self, space):
    self.strategy.to_mutable(space, self)
    self.strategy.reverse(self.str_storage) # python string
    return self
```

## [MagLev](http://maglev.github.io)
* Ruby auf der GemStone/S VM (Smalltalk), JIT Compiler
* OpenSource, aber VM für Produktionsbetrieb nur kommerziell nutzbar
* Verteilte Objektdatenbank (ACID Konform), quasi NoSQL
* MagLev 1.0.0 (Okt. 2011) ist Ruby 1.8.7 kompatibel

```
  primitive          'reverse', 'reverse'
```

## [IronRuby](http://www.ironruby.net)
* Teil der [IronLanguages](https://github.com/IronLanguages), Läuft auf der Microsoft CLR (.NET)
* Interoperabilität mit anderen Anwendungen auf der .NET Platform
* 2007 released von Microsoft, 1.0 in 2010 (Ruby 1.8.6 Support)
* 1.9 Support angekündigt für 1.x Versionen aber bisher nur teilweise umgesetzt (1.9.1)
* Nicht alle Rails Anwendungen sind lauffähig
* Entwicklung mehr oder weniger eingeschlafen (Letzter Commit vor 6 Monaten)

```c#
public MutableString Reverse() {
    MutatePreserveAsciiness();
    PrepareForCharacterWrite();

    // TODO: surrogates
    var content = _content;

    int length = content.Count;
    if (length <= 1) {
        return this;
    }

    for (int i = 0; i < length / 2; i++) {
        char a = content.GetChar(i);
        char b = content.GetChar(length - i - 1);
        content.SetChar(i, b);
        content.SetChar(length - i - 1, a);
    }

    Debug.Assert(content == _content);
    return this;
}
```

## [Opal](http://opalrb.org)
* Ruby-to-Javascript Compiler
* Anstatt Frontendsprache im Backend verwenden, lieber andersrum :)
* opal.js Runtime wird benötigt um Code auszuführen
* JS Funktionen aus Ruby verwenden (alert!!!!)
* SourceMaps

```ruby
def reverse
  `#{self}.split('').reverse().join('')` # <-- JS Quellcode
end
```

## [mRuby](https://github.com/mruby/mruby)
* Leichtgewichtige Implementierung des [ISO 30170:2012](http://www.iso.org/iso/iso_catalogue/catalogue_tc/catalogue_detail.htm?csnumber=59579) Standards
* Einbettbare Ruby VM (vgl V8 oder Lua)
* Kein require, keine Fork, keine Threads, eigene mgems
* JIT oder Ahead of Time Compiler

* Auch in [ArangoDB](http://www.arangodb.org/category/ruby/mruby)!

```c
static mrb_value mrb_str_reverse(mrb_state *mrb, mrb_value str)
{
  struct RString *s2;
  char *s, *e, *p;

  if (RSTRING(str)->len <= 1) return mrb_str_dup(mrb, str);

  s2 = str_new(mrb, 0, RSTRING(str)->len);
  str_with_class(mrb, s2, str);
  s = RSTRING_PTR(str); e = RSTRING_END(str) - 1;
  p = s2->ptr;

  while (e >= s) {
    *p++ = *e--;
  }
  return mrb_obj_value(s2);
}
```

## [webruby](https://github.com/xxuejie/webruby)
* mruby per [emscripten](https://github.com/kripken/emscripten) im Browser

## Kleines Extra: [decaf](http://trydecaf.org/)
```html
<!DOCTYPE html>
<html>
<head>
  <title>Hallo Cologne.rb</title>
  <script type="text/ruby">
  window.onload do
    list = document.create_element('ul')
    1.upto(100) do |i|
      li = document.create_element('li')
      if i%3==0 and i%5==0
        li.inner_text = "fizzbuzz"
        li.style.color = "#FF00FF"
      elsif i%3==0
        li.inner_text = "fizz"
        li.style.color = "#FF0000"
      elsif i%5==0
        li.inner_text = "buzz"
        li.style.color = "#0000FF"
      else
        li.inner_text = i.to_s
      end
      
      list.append_child li
    end
    document.body.append_child list
  end
  </script>
</head>
<body>
  
</body>
```

## Links
* [Alex Gaynor über Topaz bei den Ruby Rogues](http://rubyrogues.com/096-rr-topaz-with-alex-gaynor)
* [PyPy Blogpost über Implementierungsdetails von Topaz](http://morepypy.blogspot.de/2013/02/announcing-topaz-rpython-powered-ruby.html)
* [Getting started with mruby](http://matt.aimonetti.net/posts/2012/04/25/getting-started-with-mruby/)
* [Opal - Ruby Style!!](http://de.slideshare.net/fkchang/opal-ruby-style-ruby-in-the-browser)
* [Frank Celler beim GST004 über mruby](http://geekstammtisch.de/#GST004)
