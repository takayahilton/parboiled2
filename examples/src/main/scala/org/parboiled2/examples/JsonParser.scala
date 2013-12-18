/*
 * Copyright (C) 2009-2013 Mathias Doenitz, Alexander Myltsev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.parboiled2.examples

import org.parboiled2._
import spray.json._

/**
 * This is a high-performance, feature-complete JSON parser implementation that almost directly
 * models the JSON grammar presented at http://www.json.org as a parboiled2 PEG parser.
 */
class JsonParser(val input: ParserInput) extends Parser {
  import CharPredicate.{Digit, Digit19, HexAlpha}

  // for better performance we use a mutable StringBuilder for assembling the individual
  // (potentially escaped) characters of a JSON string
  private[this] val sb = new StringBuilder

  // the root rule
  def Json = rule { WhiteSpace ~ Value ~ EOI }

  def JsonObject: Rule1[JsObject] = rule {
    ws('{') ~ zeroOrMore(Pair).separatedBy(ws(',')) ~ ws('}') ~> ((fields: Seq[JsField]) => JsObject(fields :_*))
  }

  def Pair = rule { JsonStringUnwrapped ~ ws(':') ~ Value ~> ((_, _)) }

  def Value: Rule1[JsValue] = rule {
    JsonString | JsonNumber | JsonObject | JsonArray | JsonTrue | JsonFalse | JsonNull
  }

  def JsonString = rule { JsonStringUnwrapped ~> (JsString(_)) }

  def JsonStringUnwrapped = rule { '"' ~ run(sb.clear()) ~ Characters ~ ws('"') ~ push(sb.toString) }

  def JsonNumber = rule { capture(Integer ~ optional(Frac) ~ optional(Exp)) ~> (JsNumber(_)) ~ WhiteSpace }

  def JsonArray = rule { ws('[') ~ zeroOrMore(Value).separatedBy(ws(',')) ~ ws(']') ~> (JsArray(_ :_*)) }

  def Characters = rule { zeroOrMore('\\' ~ EscapedChar | NormalChar) }

  def EscapedChar = rule (
    QuoteSlashBackSlash ~ run(sb.append(input.charAt(cursor - 1)))
      | 'b' ~ run(sb.append('\b'))
      | 'f' ~ run(sb.append('\f'))
      | 'n' ~ run(sb.append('\n'))
      | 'r' ~ run(sb.append('\r'))
      | 't' ~ run(sb.append('\t'))
      | Unicode ~> { code => sb.append(code.asInstanceOf[Char]); () }
  )

  def NormalChar = rule { !QuoteBackSlash ~ ANY ~ run(sb.append(input.charAt(cursor - 1))) }

  def Unicode = rule { 'u' ~ capture(HexAlpha ~ HexAlpha ~ HexAlpha ~ HexAlpha) ~> (java.lang.Integer.parseInt(_, 16)) }

  def Integer = rule { optional('-') ~ (Digit19 ~ Digits | Digit) }

  def Digits = rule { oneOrMore(Digit) }

  def Frac = rule { "." ~ Digits }

  def Exp = rule { ignoreCase('e') ~ optional(anyOf("+-")) ~ Digits }

  def JsonTrue = rule { "true" ~ WhiteSpace ~ push(JsTrue) }

  def JsonFalse = rule { "false" ~ WhiteSpace ~ push(JsFalse) }

  def JsonNull = rule { "null" ~ WhiteSpace ~ push(JsNull) }

  def WhiteSpace = rule { zeroOrMore(WhiteSpaceChar) }

  def ws(c: Char) = rule { c ~ WhiteSpace }

  val WhiteSpaceChar = CharPredicate(" \n\r\t\f")
  val QuoteBackSlash = CharPredicate("\"\\")
  val QuoteSlashBackSlash = QuoteBackSlash ++ "/"
}