package com.revenge.music.lyrics

import com.atilika.kuromoji.ipadic.Tokenizer
import com.github.promeg.pinyinhelper.Pinyin
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
  val LINE_REGEX = "((\\[\\d\\d:\\d\\d\\.\\d{2,3}\\] ?)+)(.+)".toRegex()
  val TIME_REGEX = "\\[(\\d\\d):(\\d\\d)\\.(\\d{2,3})\\]".toRegex()

  private const val MINUTE_MS = 60_000L
  private const val SECOND_MS = 1_000L

  private val RICH_SYNC_LINE_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.+)".toRegex()
  private val RICH_SYNC_WORD_REGEX = "<(\\d{1,2}):(\\d{2})\\.(\\d{2,3})>\\s*([^<]+)".toRegex()
  private val AGENT_REGEX = "\\{agent:([^}]+)\\}".toRegex()
  private val BACKGROUND_REGEX = "^\\{bg\\}".toRegex()
  private val WORD_SPLIT_REGEX = "((?<=\\s|[.,!?;])|(?=\\s|[.,!?;]))".toRegex()
  private const val PUNCTUATION = ".,!?;"

  /** Parses `key=value` lines into a lookup table; keys never contain the separator. */
  private fun table(encoded: String): Map<String, String> =
    encoded
      .trimIndent()
      .lines()
      .mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
        val separator = trimmed.indexOf('=')
        if (separator <= 0) return@mapNotNull null
        trimmed.substring(0, separator) to trimmed.substring(separator + 1)
      }
      .toMap()

  // ---------------------------------------------------------------------------------------------
  // Japanese kana
  // ---------------------------------------------------------------------------------------------
  private val KANA_ROMAJI_MAP =
    table(
      """
      キャ=kya
      キュ=kyu
      キョ=kyo
      シャ=sha
      シュ=shu
      ショ=sho
      チャ=cha
      チュ=chu
      チョ=cho
      ニャ=nya
      ニュ=nyu
      ニョ=nyo
      ヒャ=hya
      ヒュ=hyu
      ヒョ=hyo
      ミャ=mya
      ミュ=myu
      ミョ=myo
      リャ=rya
      リュ=ryu
      リョ=ryo
      ギャ=gya
      ギュ=gyu
      ギョ=gyo
      ジャ=ja
      ジュ=ju
      ジョ=jo
      ヂャ=ja
      ヂュ=ju
      ヂョ=jo
      ビャ=bya
      ビュ=byu
      ビョ=byo
      ピャ=pya
      ピュ=pyu
      ピョ=pyo
      ア=a
      イ=i
      ウ=u
      エ=e
      オ=o
      カ=ka
      キ=ki
      ク=ku
      ケ=ke
      コ=ko
      サ=sa
      シ=shi
      ス=su
      セ=se
      ソ=so
      タ=ta
      チ=chi
      ツ=tsu
      テ=te
      ト=to
      ナ=na
      ニ=ni
      ヌ=nu
      ネ=ne
      ノ=no
      ハ=ha
      ヒ=hi
      フ=fu
      ヘ=he
      ホ=ho
      マ=ma
      ミ=mi
      ム=mu
      メ=me
      モ=mo
      ヤ=ya
      ユ=yu
      ヨ=yo
      ラ=ra
      リ=ri
      ル=ru
      レ=re
      ロ=ro
      ワ=wa
      ヲ=o
      ン=n
      ガ=ga
      ギ=gi
      グ=gu
      ゲ=ge
      ゴ=go
      ザ=za
      ジ=ji
      ズ=zu
      ゼ=ze
      ゾ=zo
      ダ=da
      ヂ=ji
      ヅ=zu
      デ=de
      ド=do
      バ=ba
      ビ=bi
      ブ=bu
      ベ=be
      ボ=bo
      パ=pa
      ピ=pi
      プ=pu
      ペ=pe
      ポ=po
      ー=
      """
    )

  // ---------------------------------------------------------------------------------------------
  // Korean jamo
  // ---------------------------------------------------------------------------------------------
  private val HANGUL_CHO =
    table(
      """
      ᄀ=g
      ᄁ=kk
      ᄂ=n
      ᄃ=d
      ᄄ=tt
      ᄅ=r
      ᄆ=m
      ᄇ=b
      ᄈ=pp
      ᄉ=s
      ᄊ=ss
      ᄋ=
      ᄌ=j
      ᄍ=jj
      ᄎ=ch
      ᄏ=k
      ᄐ=t
      ᄑ=p
      ᄒ=h
      """
    )

  private val HANGUL_JUNG =
    table(
      """
      ᅡ=a
      ᅢ=ae
      ᅣ=ya
      ᅤ=yae
      ᅥ=eo
      ᅦ=e
      ᅧ=yeo
      ᅨ=ye
      ᅩ=o
      ᅪ=wa
      ᅫ=wae
      ᅬ=oe
      ᅭ=yo
      ᅮ=u
      ᅯ=wo
      ᅰ=we
      ᅱ=wi
      ᅲ=yu
      ᅳ=eu
      ᅴ=eui
      ᅵ=i
      """
    )

  private val HANGUL_JONG =
    table(
      """
      ᆨ=k
      ᆨᄋ=g
      ᆨᄂ=ngn
      ᆨᄅ=ngn
      ᆨᄆ=ngm
      ᆨᄒ=kh
      ᆩ=kk
      ᆩᄋ=kg
      ᆩᄂ=ngn
      ᆩᄅ=ngn
      ᆩᄆ=ngm
      ᆩᄒ=kh
      ᆪ=k
      ᆪᄋ=ks
      ᆪᄂ=ngn
      ᆪᄅ=ngn
      ᆪᄆ=ngm
      ᆪᄒ=kch
      ᆫ=n
      ᆫᄅ=ll
      ᆬ=n
      ᆬᄋ=nj
      ᆬᄂ=nn
      ᆬᄅ=nn
      ᆬᄆ=nm
      ᆬㅎ=nch
      ᆭ=n
      ᆭᄋ=nh
      ᆭᄅ=nn
      ᆮ=t
      ᆮᄋ=d
      ᆮᄂ=nn
      ᆮᄅ=nn
      ᆮᄆ=nm
      ᆮᄒ=th
      ᆯ=l
      ᆯᄋ=r
      ᆯᄂ=ll
      ᆯᄅ=ll
      ᆰ=k
      ᆰᄋ=lg
      ᆰᄂ=ngn
      ᆰᄅ=ngn
      ᆰᄆ=ngm
      ᆰᄒ=lkh
      ᆱ=m
      ᆱᄋ=lm
      ᆱᄂ=mn
      ᆱᄅ=mn
      ᆱᄆ=mm
      ᆱᄒ=lmh
      ᆲ=p
      ᆲᄋ=lb
      ᆲᄂ=mn
      ᆲᄅ=mn
      ᆲᄆ=mm
      ᆲᄒ=lph
      ᆳ=t
      ᆳᄋ=ls
      ᆳᄂ=nn
      ᆳᄅ=nn
      ᆳᄆ=nm
      ᆳᄒ=lsh
      ᆴ=t
      ᆴᄋ=lt
      ᆴᄂ=nn
      ᆴᄅ=nn
      ᆴᄆ=nm
      ᆴᄒ=lth
      ᆵ=p
      ᆵᄋ=lp
      ᆵᄂ=mn
      ᆵᄅ=mn
      ᆵᄆ=mm
      ᆵᄒ=lph
      ᆶ=l
      ᆶᄋ=lh
      ᆶᄂ=ll
      ᆶᄅ=ll
      ᆶᄆ=lm
      ᆶᄒ=lh
      ᆷ=m
      ᆷᄅ=mn
      ᆸ=p
      ᆸᄋ=b
      ᆸᄂ=mn
      ᆸᄅ=mn
      ᆸᄆ=mm
      ᆸᄒ=ph
      ᆹ=p
      ᆹᄋ=ps
      ᆹᄂ=mn
      ᆹᄅ=mn
      ᆹᄆ=mm
      ᆹᄒ=psh
      ᆺ=t
      ᆺᄋ=s
      ᆺᄂ=nn
      ᆺᄅ=nn
      ᆺᄆ=nm
      ᆺᄒ=sh
      ᆻ=t
      ᆻᄋ=ss
      ᆻᄂ=tn
      ᆻᄅ=tn
      ᆻᄆ=nm
      ᆻᄒ=th
      ᆼ=ng
      ᆽ=t
      ᆽᄋ=j
      ᆽᄂ=nn
      ᆽᄅ=nn
      ᆽᄆ=nm
      ᆽᄒ=ch
      ᆾ=t
      ᆾᄋ=ch
      ᆾᄂ=nn
      ᆾᄅ=nn
      ᆾᄆ=nm
      ᆾᄒ=ch
      ᆿ=k
      ᆿᄋ=k
      ᆿᄂ=ngn
      ᆿᄅ=ngn
      ᆿᄆ=ngm
      ᆿᄒ=kh
      ᇀ=t
      ᇀᄋ=t
      ᇀᄂ=nn
      ᇀᄅ=nn
      ᇀᄆ=nm
      ᇀᄒ=th
      ᇁ=p
      ᇁᄋ=p
      ᇁᄂ=mn
      ᇁᄅ=mn
      ᇁᄆ=mm
      ᇁᄒ=ph
      ᇂ=t
      ᇂᄋ=h
      ᇂᄂ=nn
      ᇂᄅ=nn
      ᇂᄆ=mm
      ᇂᄒ=t
      ᇂᄀ=k
      """
    )

  // ---------------------------------------------------------------------------------------------
  // Devanagari (Hindi)
  // ---------------------------------------------------------------------------------------------
  private val DEVANAGARI_ROMAJI_MAP =
    table(
      """
      अ=a
      आ=aa
      इ=i
      ई=ee
      उ=u
      ऊ=oo
      ऋ=ri
      ए=e
      ऐ=ai
      ओ=o
      औ=au
      क=k
      ख=kh
      ग=g
      घ=gh
      ङ=ng
      च=ch
      छ=chh
      ज=j
      झ=jh
      ञ=ny
      ट=t
      ठ=th
      ड=d
      ढ=dh
      ण=n
      त=t
      थ=th
      द=d
      ध=dh
      न=n
      प=p
      फ=ph
      ब=b
      भ=bh
      म=m
      य=y
      र=r
      ल=l
      व=v
      श=sh
      ष=sh
      स=s
      ह=h
      क्ष=ksh
      त्र=tr
      ज्ञ=gy
      श्र=shr
      ा=aa
      ि=i
      ी=ee
      ु=u
      ू=oo
      ृ=ri
      े=e
      ै=ai
      ो=o
      ौ=au
      ं=n
      ः=h
      ँ=n
      ़=
      ्=
      ०=0
      १=1
      २=2
      ३=3
      ४=4
      ५=5
      ६=6
      ७=7
      ८=8
      ९=9
      ॐ=Om
      ऽ=
      क़=q
      ख़=kh
      ग़=g
      ज़=z
      ड़=r
      ढ़=rh
      फ़=f
      य़=y
      """
    ) +
      mapOf(
        "\u0915\u093C" to "q",
        "\u0916\u093C" to "kh",
        "\u0917\u093C" to "g",
        "\u091C\u093C" to "z",
        "\u0921\u093C" to "r",
        "\u0922\u093C" to "rh",
        "\u092B\u093C" to "f",
        "\u092F\u093C" to "y",
      )

  // ---------------------------------------------------------------------------------------------
  // Gurmukhi (Punjabi)
  // ---------------------------------------------------------------------------------------------
  private val GURMUKHI_ROMAJI_MAP =
    table(
      """
      ੳ=o
      ਅ=a
      ੲ=e
      ਸ=s
      ਹ=h
      ਕ=k
      ਖ=kh
      ਗ=g
      ਘ=gh
      ਙ=ng
      ਚ=ch
      ਛ=chh
      ਜ=j
      ਝ=jh
      ਞ=ny
      ਟ=t
      ਠ=th
      ਡ=d
      ਢ=dh
      ਣ=n
      ਤ=t
      ਥ=th
      ਦ=d
      ਧ=dh
      ਨ=n
      ਪ=p
      ਫ=ph
      ਬ=b
      ਭ=bh
      ਮ=m
      ਯ=y
      ਰ=r
      ਲ=l
      ਵ=v
      ੜ=r
      ਸ਼=sh
      ਖ਼=kh
      ਗ਼=g
      ਜ਼=z
      ਫ਼=f
      ਲ਼=l
      ਾ=aa
      ਿ=i
      ੀ=ee
      ੁ=u
      ੂ=oo
      ੇ=e
      ੈ=ai
      ੋ=o
      ੌ=au
      ੰ=n
      ਂ=n
      ੱ=
      ੍=
      ਼=
      ੴ=Ek Onkar
      ੦=0
      ੧=1
      ੨=2
      ੩=3
      ੪=4
      ੫=5
      ੬=6
      ੭=7
      ੮=8
      ੯=9
      """
    )

  // ---------------------------------------------------------------------------------------------
  // Cyrillic
  // ---------------------------------------------------------------------------------------------
  private val GENERAL_CYRILLIC_ROMAJI_MAP =
    table(
      """
      А=A
      Б=B
      В=V
      Г=G
      Ґ=G
      Д=D
      Ѓ=Ǵ
      Ђ=Đ
      Е=E
      Ё=Yo
      Є=Ye
      Ж=Zh
      З=Z
      Ѕ=Dz
      И=I
      І=I
      Ї=Yi
      Й=Y
      Ј=Y
      К=K
      Л=L
      Љ=Ly
      М=M
      Н=N
      Њ=Ny
      О=O
      П=P
      Р=R
      С=S
      Т=T
      Ћ=Ć
      У=U
      Ў=Ŭ
      Ф=F
      Х=Kh
      Ц=Ts
      Ч=Ch
      Џ=Dž
      Ш=Sh
      Щ=Shch
      Ъ=ʺ
      Ы=Y
      Ь=ʹ
      Э=E
      Ю=Yu
      Я=Ya
      Ѡ=O
      Ѣ=Ya
      Ѥ=Ye
      Ѧ=Ya
      Ѩ=Ya
      Ѫ=U
      Ѭ=Yu
      Ѯ=Ks
      Ѱ=Ps
      Ѳ=F
      Ѵ=I
      Ѷ=I
      Ғ=Gh
      Ҕ=G
      Җ=Zh
      Ҙ=Dz
      Қ=Q
      Ҝ=K
      Ҟ=K
      Ҡ=K
      Ң=Ng
      Ҥ=Ng
      Ҧ=P
      Ҩ=O
      Ҫ=S
      Ҭ=T
      Ү=U
      Ұ=U
      Ҳ=Kh
      Ҵ=Ts
      Ҷ=Ch
      Ҹ=Ch
      Һ=H
      Ҽ=Ch
      Ҿ=Ch
      Ќ=Ḱ
      Ө=Ö
      а=a
      б=b
      в=v
      г=g
      ґ=g
      д=d
      ѓ=ǵ
      ђ=đ
      е=e
      ё=yo
      є=ye
      ж=zh
      з=z
      ѕ=dz
      и=i
      і=i
      ї=yi
      й=y
      ј=y
      к=k
      л=l
      љ=ly
      м=m
      н=n
      њ=ny
      о=o
      п=p
      р=r
      с=s
      т=t
      ћ=ć
      у=u
      ў=ŭ
      ф=f
      х=kh
      ц=ts
      ч=ch
      џ=dž
      ш=sh
      щ=shch
      ъ=ʺ
      ы=y
      ь=ʹ
      э=e
      ю=yu
      я=ya
      ѡ=o
      ѣ=ya
      ѥ=ye
      ѧ=ya
      ѩ=ya
      ѫ=u
      ѭ=yu
      ѯ=ks
      ѱ=ps
      ѳ=f
      ѵ=i
      ѷ=i
      ғ=gh
      ҕ=g
      җ=zh
      ҙ=dz
      қ=q
      ҝ=k
      ҟ=k
      ҡ=k
      ң=ng
      ҥ=ng
      ҧ=p
      ҩ=o
      ҫ=s
      ҭ=t
      ү=u
      ұ=u
      ҳ=kh
      ҵ=ts
      ҷ=ch
      ҹ=ch
      һ=h
      ҽ=ch
      ҿ=ch
      ќ=ḱ
      ө=ö
      """
    )

  private val RUSSIAN_ROMAJI_MAP =
    mapOf("ого" to "ovo", "Ого" to "Ovo", "его" to "evo", "Его" to "Evo")

  private val UKRAINIAN_ROMAJI_MAP =
    mapOf(
      "Г" to "H",
      "г" to "h",
      "Ґ" to "G",
      "ґ" to "g",
      "Є" to "Ye",
      "є" to "ye",
      "І" to "I",
      "і" to "i",
      "Ї" to "Yi",
      "ї" to "yi",
    )

  private val SERBIAN_ROMAJI_MAP =
    mapOf(
      "Ж" to "Ž",
      "Љ" to "Lj",
      "Њ" to "Nj",
      "Ц" to "C",
      "Ч" to "Č",
      "Џ" to "Dž",
      "Ш" to "Š",
      "Х" to "H",
      "ж" to "ž",
      "љ" to "lj",
      "њ" to "nj",
      "ц" to "c",
      "ч" to "č",
      "џ" to "dž",
      "ш" to "š",
      "х" to "h",
    )

  private val BULGARIAN_ROMAJI_MAP =
    mapOf(
      "Ж" to "Zh",
      "Ц" to "Ts",
      "Ч" to "Ch",
      "Ш" to "Sh",
      "Щ" to "Sht",
      "Ъ" to "A",
      "Ь" to "Y",
      "Ю" to "Yu",
      "Я" to "Ya",
      "ж" to "zh",
      "ц" to "ts",
      "ч" to "ch",
      "ш" to "sh",
      "щ" to "sht",
      "ъ" to "a",
      "ь" to "y",
      "ю" to "yu",
      "я" to "ya",
    )

  private val BELARUSIAN_ROMAJI_MAP = mapOf("Г" to "H", "г" to "h", "Ў" to "W", "ў" to "w")

  private val KYRGYZ_ROMAJI_MAP = mapOf("Ү" to "Ü", "ү" to "ü", "Ы" to "Y", "ы" to "y")

  private val MACEDONIAN_ROMAJI_MAP =
    mapOf(
      "Ѓ" to "Gj",
      "Ѕ" to "Dz",
      "И" to "I",
      "Ј" to "J",
      "Љ" to "Lj",
      "Њ" to "Nj",
      "Ќ" to "Kj",
      "Џ" to "Dž",
      "Ч" to "Č",
      "Ш" to "Sh",
      "Ж" to "Zh",
      "Ц" to "C",
      "Х" to "H",
      "ѓ" to "gj",
      "ѕ" to "dz",
      "и" to "i",
      "ј" to "j",
      "љ" to "lj",
      "њ" to "nj",
      "ќ" to "kj",
      "џ" to "dž",
      "ч" to "č",
      "ш" to "sh",
      "ж" to "zh",
      "ц" to "c",
      "х" to "h",
    )

  // Letter inventories are used to tell closely related Cyrillic alphabets apart.
  private val RUSSIAN_LETTERS =
    "АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнопрстуфхцчшщъыьэюя".toSet()
  private val UKRAINIAN_LETTERS =
    "АБВГҐДЕЄЖЗИІЇЙКЛМНОПРСТУФХЦЧШЩЬЮЯабвгґдеєжзиіїйклмнопрстуфхцчшщьюя".toSet()
  private val UKRAINIAN_SPECIFIC = "ҐґЄєІіЇї".toSet()
  private val SERBIAN_LETTERS =
    "АБВГДЂЕЖЗИЈКЛЉМНЊОПРСТЋУФХЦЧЏШабвгдђежзијклљмнњопрстћуфхцчџш".toSet()
  private val SERBIAN_SPECIFIC = "ЂђЈјЉљЊњЋћЏџ".toSet()
  private val BULGARIAN_LETTERS =
    "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЬЮЯабвгдежзийклмнопрстуфхцчшщъьюя".toSet()
  private val BELARUSIAN_LETTERS =
    "АБВГДЕЁЖЗІЙКЛМНОПРСТУЎФХЦЧШЬЮЯЫЭабвгдеёжзійклмнопрстуўфхцчшь юяыэ".replace(" ", "").toSet()
  private val BELARUSIAN_SPECIFIC = "ЎўІі".toSet()
  private val KYRGYZ_LETTERS =
    "АБВГДЕЁЖЗИЙКЛМНҢОӨПРСТУҮФХЦЧШЩЪЫЬЭЮЯабвгдеёжзийклмнңоөпрстуүфхцчшщъыьэюя".toSet()
  private val KYRGYZ_SPECIFIC = "ҢңӨөҮү".toSet()
  private val MACEDONIAN_LETTERS =
    "АБВГДЃЕЖЗЅИЈКЛЉМНЊОПРСТЌУФХЦЧЏШабвгдѓежзѕијклљмнњопрстќуфхцчџш".toSet()
  private val MACEDONIAN_SPECIFIC = "ЃѓЅѕЌќ".toSet()
  private val CYRILLIC_RANGE = '\u0400'..'\u04FF'

  private val kuromojiTokenizer: Tokenizer by lazy { Tokenizer() }
  private val autoParser by lazy { AutoParser() }

  private val HEX_ENTITY_REGEX = "&#x([0-9a-fA-F]+);".toRegex()
  private val DEC_ENTITY_REGEX = "&#(\\d+);".toRegex()

  private fun decodeHtmlEntities(text: String): String =
    text
      .replace(HEX_ENTITY_REGEX) { match ->
        match.groupValues[1]
          .toIntOrNull(16)
          ?.takeIf { it in 0..0x10FFFF }
          ?.let { String(Character.toChars(it)) } ?: match.value
      }
      .replace(DEC_ENTITY_REGEX) { match ->
        match.groupValues[1]
          .toIntOrNull()
          ?.takeIf { it in 0..0x10FFFF }
          ?.let { String(Character.toChars(it)) } ?: match.value
      }
      .replace("&apos;", "'")
      .replace("&quot;", "\"")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&nbsp;", " ")
      .replace("&amp;", "&")

  fun hasSyncedLyrics(lyrics: String): Boolean = parseLyrics(lyrics).any { it.time > 0L }

  fun parseLyrics(lyrics: String): List<LyricsEntry> {
    val unescaped =
      lyrics
        .trim()
        .removePrefix("\"")
        .removeSuffix("\"")
        .replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")

    val decoded = decodeHtmlEntities(unescaped)
    val trimmed = decoded.trim()

    val looksStructured =
      trimmed.startsWith("<") || trimmed.contains("<tt", ignoreCase = true) ||
        trimmed.contains("<p begin", ignoreCase = true)
    val hasEnhancedTags =
      trimmed.contains("<") && trimmed.contains(">") &&
        RICH_SYNC_WORD_REGEX.containsMatchIn(trimmed)

    if (looksStructured || hasEnhancedTags || autoParser.canParse(decoded)) {
      parseWithAccompanist(decoded, looksStructured)?.let { return it }
    }

    val lines = decoded.lines().filter { it.isNotBlank() && !it.trim().startsWith("[offset:") }
    val isRichSync =
      lines.any { line ->
        RICH_SYNC_LINE_REGEX.matches(line.trim()) && RICH_SYNC_WORD_REGEX.containsMatchIn(line)
      }
    return if (isRichSync) parseRichSyncLyrics(lines) else parseStandardLyrics(lines)
  }

  private fun parseWithAccompanist(decoded: String, isStructured: Boolean): List<LyricsEntry>? {
    val parsed =
      try {
        autoParser.parse(decoded)
      } catch (e: Exception) {
        return null
      }
    if (parsed.lines.isEmpty()) return null

    val entries =
      parsed.lines.mapNotNull { line ->
        when (line) {
          is KaraokeLine -> {
            val words =
              line.syllables
                .map { syllable ->
                  WordTimestamp(
                    text = syllable.content.trim(),
                    startTime = syllable.start / 1000.0,
                    endTime = syllable.end / 1000.0,
                  )
                }
                .filter { it.text.isNotEmpty() }
            val text = line.syllables.joinToString("") { it.content }.trim()
            if (text.isBlank()) {
              null
            } else {
              LyricsEntry(
                  time = line.start.toLong(),
                  text = text,
                  words = words.ifEmpty { null },
                  isBackground = line is KaraokeLine.AccompanimentKaraokeLine,
                )
                .also { entry ->
                  if (!line.translation.isNullOrBlank()) {
                    entry.translatedTextFlow.value = line.translation
                  }
                }
            }
          }
          is SyncedLine -> {
            val text = line.content.trim()
            if (text.isBlank()) {
              null
            } else {
              LyricsEntry(time = line.start.toLong(), text = text).also { entry ->
                if (!line.translation.isNullOrBlank()) {
                  entry.translatedTextFlow.value = line.translation
                }
              }
            }
          }
          else -> null
        }
      }

    if (entries.isEmpty()) return null

    val rawLines =
      decoded.lines().filter {
        it.isNotBlank() &&
          !it.trim().startsWith("[offset:") &&
          !it.trim().startsWith("[ar:") &&
          !it.trim().startsWith("[ti:") &&
          !it.trim().startsWith("[al:") &&
          !it.trim().startsWith("[by:")
      }
    val hasWordTimings = entries.any { it.words?.isNotEmpty() == true }
    return if (isStructured || hasWordTimings || entries.size >= rawLines.size) {
      entries.sorted()
    } else {
      null
    }
  }

  private fun parseRichSyncLyrics(lines: List<String>): List<LyricsEntry> {
    val result = mutableListOf<LyricsEntry>()

    lines.forEachIndexed { index, line ->
      val match = RICH_SYNC_LINE_REGEX.matchEntire(line.trim()) ?: return@forEachIndexed
      val minutes = match.groupValues[1].toLongOrNull() ?: 0L
      val seconds = match.groupValues[2].toLongOrNull() ?: 0L
      val fraction = match.groupValues[3].toLongOrNull() ?: 0L
      val millisPart = if (match.groupValues[3].length == 3) fraction else fraction * 10
      val lineTime = minutes * MINUTE_MS + seconds * SECOND_MS + millisPart

      var content = match.groupValues[4].trimStart()
      val agentMatch = AGENT_REGEX.find(content)
      val agent = agentMatch?.groupValues?.get(1)
      if (agentMatch != null) content = content.replaceFirst(AGENT_REGEX, "")

      val isBackground = BACKGROUND_REGEX.containsMatchIn(content)
      if (isBackground) content = content.replaceFirst(BACKGROUND_REGEX, "")

      val words = parseRichSyncWords(content, index, lines)
      val plain = content.replace(Regex("<\\d{1,2}:\\d{2}\\.\\d{2,3}>\\s*"), "").trim()
      if (plain.isNotBlank()) {
        result.add(
          LyricsEntry(lineTime, plain, words, agent = agent, isBackground = isBackground),
        )
      }
    }
    return result.sorted()
  }

  private fun parseRichSyncWords(
    content: String,
    lineIndex: Int,
    allLines: List<String>,
  ): List<WordTimestamp>? {
    val matches = RICH_SYNC_WORD_REGEX.findAll(content).toList()
    if (matches.isEmpty()) return null

    fun secondsAt(match: MatchResult): Double {
      val minutes = match.groupValues[1].toLongOrNull() ?: 0L
      val seconds = match.groupValues[2].toLongOrNull() ?: 0L
      val fraction = match.groupValues[3].toLongOrNull() ?: 0L
      val fractionSeconds =
        if (match.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
      return minutes * 60.0 + seconds + fractionSeconds
    }

    val words = mutableListOf<WordTimestamp>()
    matches.forEachIndexed { index, match ->
      val start = secondsAt(match)
      val end =
        if (index < matches.lastIndex) {
          secondsAt(matches[index + 1])
        } else {
          nextLineStartSeconds(lineIndex, allLines) ?: (start + 0.5)
        }
      val text = match.groupValues[4].trim()
      if (text.isNotBlank()) words.add(WordTimestamp(text, start, end))
    }
    return words.ifEmpty { null }
  }

  private fun nextLineStartSeconds(lineIndex: Int, allLines: List<String>): Double? {
    if (lineIndex + 1 >= allLines.size) return null
    val match = RICH_SYNC_LINE_REGEX.matchEntire(allLines[lineIndex + 1].trim()) ?: return null
    val minutes = match.groupValues[1].toLongOrNull() ?: return null
    val seconds = match.groupValues[2].toLongOrNull() ?: return null
    val fraction = match.groupValues[3].toLongOrNull() ?: 0L
    val fractionSeconds =
      if (match.groupValues[3].length == 3) fraction / 1000.0 else fraction / 100.0
    return minutes * 60.0 + seconds + fractionSeconds
  }

  private fun parseStandardLyrics(lines: List<String>): List<LyricsEntry> {
    val result = mutableListOf<LyricsEntry>()
    var i = 0
    while (i < lines.size) {
      val line = lines[i]
      val isWordTagLine = line.trim().startsWith("<") && line.trim().endsWith(">")
      if (!isWordTagLine) {
        val entries = parseLine(line)
        if (entries != null) {
          val words =
            if (i + 1 < lines.size) {
              val next = lines[i + 1]
              if (next.trim().startsWith("<") && next.trim().endsWith(">")) {
                parseWordTimestamps(next.trim().removeSurrounding("<", ">"))
              } else {
                null
              }
            } else {
              null
            }
          if (words != null) {
            result.addAll(
              entries.map {
                LyricsEntry(
                  it.time,
                  it.text,
                  words,
                  agent = it.agent,
                  isBackground = it.isBackground,
                )
              },
            )
          } else {
            result.addAll(entries)
          }
        }
      }
      i++
    }
    return result.sorted()
  }

  private fun parseWordTimestamps(data: String): List<WordTimestamp>? {
    if (data.isBlank()) return null
    return data.split("|").mapNotNull { wordData ->
      val parts = wordData.split(":")
      if (parts.size == 3) {
        WordTimestamp(
          text = parts[0],
          startTime = parts[1].toDoubleOrNull() ?: 0.0,
          endTime = parts[2].toDoubleOrNull() ?: 0.0,
        )
      } else {
        null
      }
    }
  }

  private fun parseLine(line: String): List<LyricsEntry>? {
    if (line.isEmpty()) return null
    val match = LINE_REGEX.matchEntire(line.trim()) ?: return null
    var text = match.groupValues[3]

    val agentMatch = AGENT_REGEX.find(text)
    val agent = agentMatch?.groupValues?.get(1)
    if (agentMatch != null) text = text.replaceFirst(AGENT_REGEX, "")

    val isBackground = BACKGROUND_REGEX.containsMatchIn(text)
    if (isBackground) text = text.replaceFirst(BACKGROUND_REGEX, "")

    return TIME_REGEX.findAll(match.groupValues[1])
      .map { time ->
        val minutes = time.groupValues[1].toLongOrNull() ?: 0L
        val seconds = time.groupValues[2].toLongOrNull() ?: 0L
        val fractionText = time.groupValues[3]
        var millis = fractionText.toLongOrNull() ?: 0L
        if (fractionText.length == 2) millis *= 10
        LyricsEntry(
          minutes * MINUTE_MS + seconds * SECOND_MS + millis,
          text,
          agent = agent,
          isBackground = isBackground,
        )
      }
      .toList()
  }

  fun findCurrentLineIndex(
    lines: List<LyricsEntry>,
    position: Long,
  ): Int {
    for (index in lines.indices) {
      if (lines[index].time >= position + 300L) return index - 1
    }
    return lines.lastIndex
  }

  fun katakanaToRomaji(katakana: String?): String = romajiFromKana(katakana, null)

  fun katakanaToRomaji(katakana: String?, nextKatakana: String? = null): String =
    romajiFromKana(katakana, nextKatakana)

  private fun romajiFromKana(katakana: String?, nextKatakana: String?): String {
    if (katakana.isNullOrEmpty()) return ""

    val out = StringBuilder(katakana.length)
    var i = 0
    while (i < katakana.length) {
      var consumed = false

      if (i + 1 < katakana.length) {
        val pair = KANA_ROMAJI_MAP[katakana.substring(i, i + 2)]
        if (pair != null) {
          out.append(pair)
          i += 2
          consumed = true
        }
      }

      if (!consumed && katakana[i] == 'ッ') {
        val nextChar = nextKatakana?.getOrNull(0)
        if (nextChar != null) {
          val first = KANA_ROMAJI_MAP[nextChar.toString()]?.getOrNull(0)?.toString()
          out.append((first ?: nextChar.toString()).lowercase().trim())
        }
        i += 1
        consumed = true
      }

      if (!consumed) {
        val single = katakana[i].toString()
        out.append(KANA_ROMAJI_MAP[single] ?: single)
        i += 1
      }
    }
    return out.toString().lowercase()
  }

  suspend fun romanizeJapanese(text: String): String =
    withContext(Dispatchers.Default) {
      val tokens = kuromojiTokenizer.tokenize(text)
      tokens
        .mapIndexed { index, token ->
          val reading =
            token.reading?.takeIf { it.isNotEmpty() && it != "*" } ?: token.surface
          val nextReading =
            if (index + 1 < tokens.size) {
              tokens[index + 1].reading?.takeIf { it.isNotEmpty() && it != "*" }
                ?: tokens[index + 1].surface
            } else {
              null
            }
          katakanaToRomaji(reading, nextReading)
        }
        .joinToString(" ")
    }

  suspend fun romanizeKorean(text: String): String =
    withContext(Dispatchers.Default) {
      val out = StringBuilder()
      var pendingFinal: String? = null

      fun flush(jongKey: String) {
        out.append(HANGUL_JONG[jongKey] ?: jongKey)
      }

      for (char in text) {
        if (char in '\uAC00'..'\uD7A3') {
          val syllableIndex = char.code - 0xAC00
          val choIndex = syllableIndex / (21 * 28)
          val jungIndex = (syllableIndex % (21 * 28)) / 28
          val jongIndex = syllableIndex % 28

          val choChar = (0x1100 + choIndex).toChar().toString()
          val jungChar = (0x1161 + jungIndex).toChar().toString()
          val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

          pendingFinal?.let { previous ->
            flush(HANGUL_JONG[previous + choChar] ?: HANGUL_JONG[previous] ?: previous)
          }
          out.append(HANGUL_CHO[choChar] ?: choChar)
          out.append(HANGUL_JUNG[jungChar] ?: jungChar)
          pendingFinal = jongChar
        } else {
          pendingFinal?.let { previous ->
            flush(HANGUL_JONG[previous] ?: previous)
            pendingFinal = null
          }
          out.append(char)
        }
      }

      pendingFinal?.let { previous -> flush(HANGUL_JONG[previous] ?: previous) }
      out.toString()
    }

  suspend fun romanizeChinese(text: String): String =
    withContext(Dispatchers.Default) {
      if (text.isEmpty()) return@withContext ""
      val out = StringBuilder(text.length * 2)
      for (char in text) {
        if (char in '\u4E00'..'\u9FFF') {
          out.append(Pinyin.toPinyin(char).lowercase(Locale.getDefault())).append(' ')
        } else {
          out.append(char)
        }
      }
      out
        .toString()
        .replace(Regex("\\s+([,.!?;:])"), "$1")
        .replace(Regex("\\s+([，。！？；：、（）《》〈〉【】『』「」])"), "$1")
        .trim()
    }

  suspend fun romanizeCyrillic(text: String): String? =
    withContext(Dispatchers.Default) {
      if (text.isEmpty()) return@withContext null
      val cyrillic = text.filter { it in CYRILLIC_RANGE }
      if (cyrillic.isEmpty() || (cyrillic.length == 1 && cyrillic[0] in "еЕ")) {
        return@withContext null
      }

      when {
        isRussian(text) -> romanizeRussian(text)
        isUkrainian(text) -> romanizeUkrainian(text)
        isSerbian(text) -> romanizeCharByChar(text, SERBIAN_ROMAJI_MAP)
        isBulgarian(text) -> romanizeCharByChar(text, BULGARIAN_ROMAJI_MAP)
        isBelarusian(text) -> romanizeBelarusian(text)
        isKyrgyz(text) -> romanizeCharByChar(text, KYRGYZ_ROMAJI_MAP)
        isMacedonian(text) -> romanizeCharByChar(text, MACEDONIAN_ROMAJI_MAP)
        else -> null
      }
    }

  /** Splits into words, whitespace and punctuation so the caller only maps letter runs. */
  private fun forEachToken(text: String, block: (String) -> Unit) {
    WORD_SPLIT_REGEX.split(text).forEach { token ->
      if (token.isNotEmpty()) block(token)
    }
  }

  private fun isPlainToken(token: String): Boolean =
    token.isBlank() || (token.length == 1 && token[0] in PUNCTUATION)

  private fun romanizeCharByChar(text: String, table: Map<String, String>): String {
    val out = StringBuilder(text.length)
    forEachToken(text) { token ->
      if (isPlainToken(token)) {
        out.append(token)
      } else {
        for (char in token) {
          val key = char.toString()
          out.append(table[key] ?: GENERAL_CYRILLIC_ROMAJI_MAP[key] ?: key)
        }
      }
    }
    return out.toString()
  }

  private fun romanizeRussian(text: String): String {
    val out = StringBuilder(text.length)
    forEachToken(text) { token ->
      if (isPlainToken(token)) {
        out.append(token)
      } else {
        var i = 0
        while (i < token.length) {
          val triple = if (i + 2 < token.length) token.substring(i, i + 3) else null
          val special = triple?.let(RUSSIAN_ROMAJI_MAP::get)
          if (special != null) {
            out.append(special)
            i += 3
            continue
          }
          val char = token[i].toString()
          out.append(
            if ((char == "е" || char == "Е") && i == 0) {
              if (char == "е") "ye" else "Ye"
            } else {
              GENERAL_CYRILLIC_ROMAJI_MAP[char] ?: char
            },
          )
          i++
        }
      }
    }
    return out.toString()
  }

  private fun romanizeUkrainian(text: String): String {
    val out = StringBuilder(text.length)
    forEachToken(text) { token ->
      if (isPlainToken(token)) {
        out.append(token)
      } else {
        var i = 0
        while (i < token.length) {
          val char = token[i]
          val key = char.toString()
          val previous = token.getOrNull(i - 1)
          val afterConsonant = previous != null && previous.isLetter() && !isCyrillicVowel(previous)
          val override = if (afterConsonant) ukrainianAfterConsonant(char) else null
          out.append(
            override ?: UKRAINIAN_ROMAJI_MAP[key] ?: GENERAL_CYRILLIC_ROMAJI_MAP[key] ?: key,
          )
          i++
        }
      }
    }
    return out.toString()
  }

  private fun ukrainianAfterConsonant(char: Char): String? =
    when (char) {
      'Ю' -> "Iu"
      'ю' -> "iu"
      'Я' -> "Ia"
      'я' -> "ia"
      else -> null
    }

  private fun romanizeBelarusian(text: String): String {
    val out = StringBuilder(text.length)
    forEachToken(text) { token ->
      if (isPlainToken(token)) {
        out.append(token)
      } else {
        var i = 0
        while (i < token.length) {
          val char = token[i].toString()
          out.append(
            if ((char == "е" || char == "Е") && i == 0) {
              if (char == "е") "ye" else "Ye"
            } else {
              BELARUSIAN_ROMAJI_MAP[char] ?: GENERAL_CYRILLIC_ROMAJI_MAP[char] ?: char
            },
          )
          i++
        }
      }
    }
    return out.toString()
  }

  fun isRussian(text: String): Boolean =
    text.any { it in RUSSIAN_LETTERS } &&
      text.all { it in RUSSIAN_LETTERS || it !in CYRILLIC_RANGE }

  fun isUkrainian(text: String): Boolean =
    text.any { it in UKRAINIAN_LETTERS || it in UKRAINIAN_SPECIFIC } &&
      text.all { it in UKRAINIAN_LETTERS || it in UKRAINIAN_SPECIFIC || it !in CYRILLIC_RANGE }

  fun isSerbian(text: String): Boolean =
    text.any { it in SERBIAN_LETTERS || it in SERBIAN_SPECIFIC } &&
      text.all { it in SERBIAN_LETTERS || it in SERBIAN_SPECIFIC || it !in CYRILLIC_RANGE }

  fun isBulgarian(text: String): Boolean =
    text.any { it in BULGARIAN_LETTERS } &&
      text.all { it in BULGARIAN_LETTERS || it !in CYRILLIC_RANGE }

  fun isBelarusian(text: String): Boolean =
    text.any { it in BELARUSIAN_LETTERS || it in BELARUSIAN_SPECIFIC } &&
      text.all { it in BELARUSIAN_LETTERS || it in BELARUSIAN_SPECIFIC || it !in CYRILLIC_RANGE }

  fun isKyrgyz(text: String): Boolean =
    text.any { it in KYRGYZ_LETTERS || it in KYRGYZ_SPECIFIC } &&
      text.all { it in KYRGYZ_LETTERS || it in KYRGYZ_SPECIFIC || it !in CYRILLIC_RANGE }

  fun isMacedonian(text: String): Boolean =
    text.any { it in MACEDONIAN_LETTERS || it in MACEDONIAN_SPECIFIC } &&
      text.all { it in MACEDONIAN_LETTERS || it in MACEDONIAN_SPECIFIC || it !in CYRILLIC_RANGE }

  fun isJapanese(text: String): Boolean =
    text.any { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' || it in '\u4E00'..'\u9FFF' }

  fun isKorean(text: String): Boolean = text.any { it in '\uAC00'..'\uD7A3' }

  fun isChinese(text: String): Boolean {
    if (text.isEmpty()) return false
    val cjk = text.count { it in '\u4E00'..'\u9FFF' }
    if (cjk == 0) return false
    val kana = text.count { it in '\u3040'..'\u309F' || it in '\u30A0'..'\u30FF' }
    return kana.toDouble() / text.length < 0.1
  }

  fun isHindi(text: String): Boolean = text.any { it in '\u0900'..'\u097F' }

  suspend fun romanizeHindi(text: String): String =
    withContext(Dispatchers.Default) {
      val out = StringBuilder(text.length)
      var i = 0
      while (i < text.length) {
        val pair = if (i + 1 < text.length) text.substring(i, i + 2) else null
        val mappedPair = pair?.let(DEVANAGARI_ROMAJI_MAP::get)
        if (mappedPair != null) {
          out.append(mappedPair)
          i += 2
        } else {
          val key = text[i].toString()
          out.append(DEVANAGARI_ROMAJI_MAP[key] ?: key)
          i += 1
        }
      }
      out.toString()
    }

  fun isPunjabi(text: String): Boolean = text.any { it in '\u0A00'..'\u0A7F' }

  suspend fun romanizePunjabi(text: String): String =
    withContext(Dispatchers.Default) {
      val out = StringBuilder(text.length)
      var i = 0
      while (i < text.length) {
        val char = text[i]

        // Addak doubles the following consonant; emit its first roman letter instead.
        if (char == '\u0A71') {
          val next = text.getOrNull(i + 1)?.toString()
          next?.let(GURMUKHI_ROMAJI_MAP::get)?.firstOrNull()?.let { out.append(it) }
          i++
          continue
        }

        val pair = if (i + 1 < text.length) text.substring(i, i + 2) else null
        val mappedPair = pair?.let(GURMUKHI_ROMAJI_MAP::get)
        if (mappedPair != null) {
          out.append(mappedPair)
          i += 2
        } else {
          val key = char.toString()
          out.append(GURMUKHI_ROMAJI_MAP[key] ?: key)
          i += 1
        }
      }
      out.toString()
    }

  private fun isCyrillicVowel(char: Char): Boolean =
    "АаЕеЄєИиІіЇїОоУуЮюЯяЫыЭэ".contains(char)
}
