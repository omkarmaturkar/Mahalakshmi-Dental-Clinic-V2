@file:OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.mahalakshmi.dental

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.min

// ───────────── Patient education library ─────────────
// Pictures are drawn by the app itself: no internet, almost no space. Text in English, Hindi and Marathi.

private object EduArt {
    val panel = Color(0xFFF7F4EE)
    val bone = Color(0xFFEAD7C3)
    val boneEdge = Color(0xFFD5BB9C)
    val gum = Color(0xFFF2A3A6)
    val gumSore = Color(0xFFE35D63)
    val tooth = Color(0xFFFFFFFF)
    val line = Color(0xFFC2B28F)
    val pulp = Color(0xFFF6B3B3)
    val pulpBad = Color(0xFFE5484D)
    val rootFill = Color(0xFFF59E0B)
    val core = Color(0xFFB9C0C6)
    val decay = Color(0xFF7B4A23)
    val filling = Color(0xFFEDE0C2)
    val crown = Color(0xFFFFF8E7)
    val crownLine = Color(0xFFD4B483)
    val metal = Color(0xFF9AA3AB)
    val metalDark = Color(0xFF5F6872)
    val tartar = Color(0xFFC9A227)
    val red = Color(0xFFE5484D)
}

/** What one picture shows. kind: 0 back tooth, 1 gap / implant, 2 front teeth, 3 wisdom tooth. */
private data class EduScene(
    val kind: Int = 0,
    val decay: Int = 0,
    val hole: Boolean = false,
    val filling: Boolean = false,
    val pulp: Int = 0,
    val abscess: Boolean = false,
    val crown: Boolean = false,
    val stump: Boolean = false,
    val broken: Boolean = false,
    val lift: Float = 0f,
    val socket: Boolean = false,
    val tartar: Boolean = false,
    val sore: Boolean = false,
    val boneLoss: Boolean = false,
    val implant: Int = 0,
    val braces: Int = 0,
    val wisdom: Int = 0
)

private class EduStep(val scene: EduScene, val en: String, val hi: String, val mr: String) {
    fun text(lang: Int) = when (lang) {
        1 -> hi
        2 -> mr
        else -> en
    }
}

private class EduTopic(
    val id: String,
    val en: String,
    val hi: String,
    val mr: String,
    val keys: List<String>,
    val thumb: Int,
    val steps: List<EduStep>
) {
    fun title(lang: Int) = when (lang) {
        1 -> hi
        2 -> mr
        else -> en
    }
}

private val EDU_TOPICS: List<EduTopic> = listOf(
    EduTopic(
        "cavity", "Cavity & filling", "कैविटी और फिलिंग", "कीड आणि फिलिंग",
        listOf("filling", "cavity", "composite", "gic", "restoration"), 1,
        listOf(
            EduStep(
                EduScene(decay = 1),
                "A cavity starts as a small hole in the hard outer layer of the tooth. At first it usually does not hurt.",
                "कैविटी दाँत की सख्त बाहरी परत में एक छोटे छेद से शुरू होती है। शुरुआत में आमतौर पर दर्द नहीं होता।",
                "कीड दाताच्या कठीण बाहेरील थरात एका लहान छिद्रापासून सुरू होते. सुरुवातीला सहसा दुखत नाही."
            ),
            EduStep(
                EduScene(decay = 2),
                "If it is not treated, the decay spreads deeper and the tooth becomes sensitive to cold and sweets.",
                "इलाज न होने पर सड़न अंदर तक फैल जाती है और दाँत में ठंडा व मीठा लगने लगता है।",
                "उपचार न केल्यास कीड आत पसरते आणि दाताला थंड व गोड लागू लागते."
            ),
            EduStep(
                EduScene(hole = true),
                "The doctor removes the decayed part and cleans the tooth.",
                "डॉक्टर सड़ा हुआ हिस्सा निकालकर दाँत को साफ करते हैं।",
                "डॉक्टर किडलेला भाग काढून दात स्वच्छ करतात."
            ),
            EduStep(
                EduScene(filling = true),
                "The hole is sealed with a tooth-coloured filling, and the tooth is strong again.",
                "छेद को दाँत के रंग की फिलिंग से भर दिया जाता है और दाँत फिर से मज़बूत हो जाता है।",
                "छिद्र दाताच्या रंगाच्या फिलिंगने भरले जाते आणि दात पुन्हा मजबूत होतो."
            )
        )
    ),
    EduTopic(
        "rct", "Root canal", "रूट कैनाल", "रूट कॅनाल",
        listOf("root canal", "rct"), 0,
        listOf(
            EduStep(
                EduScene(decay = 3, pulp = 1),
                "The decay has reached the nerve inside the tooth. This causes pain and swelling.",
                "सड़न दाँत के अंदर की नस तक पहुँच गई है। इससे दर्द और सूजन होती है।",
                "कीड दाताच्या आतल्या नसेपर्यंत पोहोचली आहे. त्यामुळे दुखणे आणि सूज येते."
            ),
            EduStep(
                EduScene(decay = 3, pulp = 1, abscess = true),
                "Without treatment, the infection spreads into the bone at the tip of the root.",
                "इलाज न होने पर संक्रमण जड़ के सिरे पर हड्डी तक फैल जाता है।",
                "उपचार न केल्यास संसर्ग मुळाच्या टोकाशी हाडापर्यंत पसरतो."
            ),
            EduStep(
                EduScene(pulp = 2, filling = true),
                "In a root canal, the infected nerve is removed, and the canals are cleaned and sealed.",
                "रूट कैनाल में संक्रमित नस निकाली जाती है और नलिकाओं को साफ करके बंद किया जाता है।",
                "रूट कॅनालमध्ये संसर्ग झालेली नस काढली जाते आणि नलिका स्वच्छ करून बंद केल्या जातात."
            ),
            EduStep(
                EduScene(pulp = 2, crown = true),
                "A crown (cap) is fixed on top to protect the tooth. It can then last for many years.",
                "दाँत को बचाने के लिए ऊपर क्राउन (कैप) लगाया जाता है। इसके बाद दाँत कई सालों तक चलता है।",
                "दाताच्या संरक्षणासाठी वर क्राउन (कॅप) बसवली जाते. त्यानंतर दात अनेक वर्षे टिकतो."
            )
        )
    ),
    EduTopic(
        "extraction", "Tooth removal", "दाँत निकालना", "दात काढणे",
        listOf("extract"), 1,
        listOf(
            EduStep(
                EduScene(decay = 3, pulp = 1, broken = true),
                "Some teeth are too broken or too loose to be saved.",
                "कुछ दाँत इतने टूटे या ढीले होते हैं कि उन्हें बचाया नहीं जा सकता।",
                "काही दात इतके तुटलेले किंवा हलणारे असतात की ते वाचवता येत नाहीत."
            ),
            EduStep(
                EduScene(decay = 3, pulp = 1, broken = true, lift = 16f),
                "After numbing the area, the doctor gently removes the tooth.",
                "उस जगह को सुन्न करने के बाद डॉक्टर धीरे से दाँत निकालते हैं।",
                "तो भाग बधिर केल्यानंतर डॉक्टर हळुवारपणे दात काढतात."
            ),
            EduStep(
                EduScene(socket = true),
                "The gap heals in a few weeks. Ask us about replacing the tooth with an implant or a bridge.",
                "खाली जगह कुछ हफ्तों में भर जाती है। इम्प्लांट या ब्रिज से नया दाँत लगवाने के बारे में हमसे पूछें।",
                "रिकामी जागा काही आठवड्यांत भरून येते. इम्प्लांट किंवा ब्रिजने नवीन दात बसवण्याबद्दल आम्हाला विचारा."
            )
        )
    ),
    EduTopic(
        "crown", "Crown (cap)", "क्राउन (कैप)", "क्राउन (कॅप)",
        listOf("crown", "cap", "bridge"), 2,
        listOf(
            EduStep(
                EduScene(broken = true, filling = true),
                "A tooth that is broken, has a large filling or has had a root canal becomes weak.",
                "टूटा हुआ, बड़ी फिलिंग वाला या रूट कैनाल किया हुआ दाँत कमज़ोर हो जाता है।",
                "तुटलेला, मोठे फिलिंग असलेला किंवा रूट कॅनाल केलेला दात कमकुवत होतो."
            ),
            EduStep(
                EduScene(stump = true),
                "The tooth is shaped, and a measurement is taken for the crown.",
                "दाँत को आकार दिया जाता है और क्राउन के लिए नाप लिया जाता है।",
                "दाताला आकार दिला जातो आणि क्राउनसाठी माप घेतले जाते."
            ),
            EduStep(
                EduScene(stump = true, crown = true),
                "A custom-made crown is fixed over the tooth. It looks and works like a natural tooth.",
                "दाँत पर नाप से बना क्राउन लगाया जाता है। यह असली दाँत जैसा दिखता और काम करता है।",
                "दातावर मापानुसार बनवलेली क्राउन बसवली जाते. ती नैसर्गिक दातासारखी दिसते आणि काम करते."
            )
        )
    ),
    EduTopic(
        "implant", "Implant", "इम्प्लांट", "इम्प्लांट",
        listOf("implant"), 3,
        listOf(
            EduStep(
                EduScene(kind = 1, implant = 0),
                "A missing tooth leaves a gap. The teeth next to it can tilt, and the bone slowly shrinks.",
                "दाँत न होने से जगह खाली रहती है। पास के दाँत झुक सकते हैं और हड्डी धीरे-धीरे घटती है।",
                "दात नसल्याने जागा रिकामी राहते. शेजारचे दात झुकू शकतात आणि हाड हळूहळू कमी होते."
            ),
            EduStep(
                EduScene(kind = 1, implant = 1),
                "A small titanium screw (the implant) is placed in the bone as a new root.",
                "हड्डी में नई जड़ की तरह एक छोटा टाइटेनियम स्क्रू (इम्प्लांट) लगाया जाता है।",
                "हाडात नवीन मुळासारखा एक लहान टायटॅनियम स्क्रू (इम्प्लांट) बसवला जातो."
            ),
            EduStep(
                EduScene(kind = 1, implant = 2),
                "Over about 3 months, the bone grows firmly around the implant.",
                "लगभग 3 महीनों में हड्डी इम्प्लांट के चारों ओर मज़बूती से जुड़ जाती है।",
                "सुमारे 3 महिन्यांत हाड इम्प्लांटभोवती घट्ट जुळून येते."
            ),
            EduStep(
                EduScene(kind = 1, implant = 3),
                "A crown is fixed on top. It looks, feels and works like a natural tooth.",
                "ऊपर क्राउन लगाया जाता है। यह असली दाँत जैसा दिखता, लगता और काम करता है।",
                "वर क्राउन बसवली जाते. ती नैसर्गिक दातासारखी दिसते, जाणवते आणि काम करते."
            )
        )
    ),
    EduTopic(
        "braces", "Braces", "ब्रेसेस", "ब्रेसेस",
        listOf("brace", "ortho", "aligner"), 1,
        listOf(
            EduStep(
                EduScene(kind = 2, braces = 0),
                "Crooked or crowded teeth are harder to clean and can affect how you bite and chew.",
                "टेढ़े-मेढ़े दाँत साफ करना मुश्किल होता है और इनसे काटने व चबाने पर असर पड़ सकता है।",
                "वाकडे-तिकडे दात स्वच्छ करणे कठीण असते आणि त्यामुळे चावण्यावर परिणाम होऊ शकतो."
            ),
            EduStep(
                EduScene(kind = 2, braces = 1),
                "Small brackets are fixed on the teeth and joined with a thin wire.",
                "दाँतों पर छोटे ब्रैकेट लगाए जाते हैं और उन्हें पतले तार से जोड़ा जाता है।",
                "दातांवर लहान ब्रॅकेट्स लावले जातात आणि ते पातळ तारेने जोडले जातात."
            ),
            EduStep(
                EduScene(kind = 2, braces = 2),
                "The wire is adjusted every 4 to 6 weeks to move the teeth slowly into place.",
                "हर 4 से 6 हफ्ते में तार को एडजस्ट किया जाता है, जिससे दाँत धीरे-धीरे सही जगह पर आते हैं।",
                "दर 4 ते 6 आठवड्यांनी तार अॅडजस्ट केली जाते, ज्यामुळे दात हळूहळू योग्य जागी येतात."
            ),
            EduStep(
                EduScene(kind = 2, braces = 3),
                "After braces, a retainer keeps the teeth in their new position.",
                "ब्रेसेस के बाद रिटेनर दाँतों को उनकी नई जगह पर बनाए रखता है।",
                "ब्रेसेसनंतर रिटेनर दातांना त्यांच्या नवीन जागी टिकवून ठेवतो."
            )
        )
    ),
    EduTopic(
        "gums", "Gum disease", "मसूड़ों की बीमारी", "हिरड्यांचे आजार",
        listOf("scaling", "clean", "gum", "perio", "polish"), 1,
        listOf(
            EduStep(
                EduScene(tartar = true),
                "Plaque and tartar build up where the gum meets the tooth.",
                "जहाँ मसूड़ा दाँत से मिलता है, वहाँ प्लाक और टार्टर जमा हो जाते हैं।",
                "जिथे हिरडी दाताला मिळते तिथे प्लाक आणि टार्टर साचतात."
            ),
            EduStep(
                EduScene(tartar = true, sore = true),
                "The gums become red and swollen, and bleed while brushing.",
                "मसूड़े लाल और सूजे हुए हो जाते हैं और ब्रश करते समय खून आता है।",
                "हिरड्या लाल होऊन सुजतात आणि ब्रश करताना रक्त येते."
            ),
            EduStep(
                EduScene(tartar = true, sore = true, boneLoss = true),
                "If ignored, the bone around the tooth is lost, gum pockets form and the tooth becomes loose.",
                "ध्यान न देने पर दाँत के आसपास की हड्डी घटती है, मसूड़ों में पॉकेट बन जाते हैं और दाँत हिलने लगता है।",
                "दुर्लक्ष केल्यास दाताभोवतीचे हाड कमी होते, हिरडीत पोकळी (पॉकेट) तयार होते आणि दात हलू लागतो."
            ),
            EduStep(
                EduScene(),
                "Deep cleaning removes the tartar. With good brushing and regular check-ups, the gums heal.",
                "डीप क्लीनिंग से टार्टर हटाया जाता है। अच्छे से ब्रश करने और नियमित जाँच से मसूड़े ठीक हो जाते हैं।",
                "डीप क्लीनिंगने टार्टर काढला जातो. नीट ब्रश केल्याने आणि नियमित तपासणीने हिरड्या बऱ्या होतात."
            )
        )
    ),
    EduTopic(
        "wisdom", "Wisdom tooth", "अक्ल दाढ़", "अक्कलदाढ",
        listOf("wisdom", "impact", "third molar"), 0,
        listOf(
            EduStep(
                EduScene(kind = 3, wisdom = 0),
                "A wisdom tooth often does not have enough space and can grow tilted under the gum.",
                "अक्ल दाढ़ को अक्सर पूरी जगह नहीं मिलती और वह मसूड़े के नीचे तिरछी उग सकती है।",
                "अक्कलदाढेला अनेकदा पुरेशी जागा मिळत नाही आणि ती हिरडीखाली तिरकी येऊ शकते."
            ),
            EduStep(
                EduScene(kind = 3, wisdom = 1),
                "Food gets trapped, causing swelling and pain, and the tooth in front can be damaged.",
                "इसमें खाना फँसता है, जिससे सूजन और दर्द होता है और सामने वाले दाँत को नुकसान पहुँच सकता है।",
                "तिथे अन्न अडकते, त्यामुळे सूज आणि दुखणे होते आणि पुढच्या दाताचे नुकसान होऊ शकते."
            ),
            EduStep(
                EduScene(kind = 3, wisdom = 2),
                "The doctor may advise removing it. The area usually heals in one to two weeks.",
                "डॉक्टर इसे निकालने की सलाह दे सकते हैं। यह जगह आमतौर पर एक-दो हफ्ते में ठीक हो जाती है।",
                "डॉक्टर ती काढण्याचा सल्ला देऊ शकतात. ती जागा सहसा एक-दोन आठवड्यांत बरी होते."
            )
        )
    )
)

// ───────────── Drawing (in a 200 × 170 box) ─────────────
private fun eduMolar() = Path().apply {
    moveTo(60f, 40f)
    quadraticBezierTo(60f, 12f, 100f, 12f)
    quadraticBezierTo(140f, 12f, 140f, 40f)
    lineTo(136f, 92f)
    lineTo(124f, 148f)
    quadraticBezierTo(118f, 156f, 112f, 146f)
    lineTo(104f, 100f)
    lineTo(96f, 100f)
    lineTo(88f, 146f)
    quadraticBezierTo(82f, 156f, 76f, 148f)
    lineTo(64f, 92f)
    close()
}

private fun eduStump() = Path().apply {
    moveTo(68f, 46f)
    lineTo(74f, 30f)
    quadraticBezierTo(100f, 24f, 126f, 30f)
    lineTo(132f, 46f)
    lineTo(136f, 92f)
    lineTo(124f, 148f)
    quadraticBezierTo(118f, 156f, 112f, 146f)
    lineTo(104f, 100f)
    lineTo(96f, 100f)
    lineTo(88f, 146f)
    quadraticBezierTo(82f, 156f, 76f, 148f)
    lineTo(64f, 92f)
    close()
}

private fun eduRoots() = Path().apply {
    moveTo(64f, 92f)
    lineTo(76f, 148f)
    quadraticBezierTo(82f, 156f, 88f, 146f)
    lineTo(96f, 100f)
    lineTo(104f, 100f)
    lineTo(112f, 146f)
    quadraticBezierTo(118f, 156f, 124f, 148f)
    lineTo(136f, 92f)
}

private fun eduPulp() = Path().apply {
    moveTo(84f, 44f)
    quadraticBezierTo(100f, 34f, 116f, 44f)
    lineTo(112f, 92f)
    lineTo(107f, 138f)
    lineTo(103f, 98f)
    lineTo(97f, 98f)
    lineTo(93f, 138f)
    lineTo(88f, 92f)
    close()
}

private fun eduChamber() = Path().apply {
    moveTo(84f, 44f)
    quadraticBezierTo(100f, 34f, 116f, 44f)
    lineTo(114f, 74f)
    lineTo(86f, 74f)
    close()
}

private fun eduDecay(level: Int) = Path().apply {
    when (level) {
        1 -> {
            moveTo(116f, 14f)
            quadraticBezierTo(127f, 15f, 130f, 23f)
            lineTo(121f, 27f)
            close()
        }
        2 -> {
            moveTo(110f, 13f)
            quadraticBezierTo(130f, 14f, 133f, 28f)
            lineTo(124f, 38f)
            lineTo(112f, 31f)
            close()
        }
        else -> {
            moveTo(106f, 12f)
            quadraticBezierTo(131f, 13f, 135f, 32f)
            lineTo(117f, 47f)
            lineTo(107f, 40f)
            close()
        }
    }
}

private fun eduHole() = Path().apply {
    moveTo(108f, 12f)
    lineTo(132f, 14f)
    lineTo(130f, 31f)
    lineTo(111f, 33f)
    close()
}

private fun eduBrokenPart() = Path().apply {
    moveTo(54f, 4f)
    lineTo(90f, 4f)
    lineTo(90f, 12.5f)
    lineTo(84f, 18f)
    lineTo(74f, 20f)
    lineTo(68f, 34f)
    lineTo(54f, 40f)
    close()
}

private fun eduCrack() = Path().apply {
    moveTo(90f, 12.5f)
    lineTo(84f, 18f)
    lineTo(74f, 20f)
    lineTo(68f, 34f)
    lineTo(60f, 40f)
    moveTo(84f, 18f)
    lineTo(92f, 42f)
}

private fun eduCap() = Path().apply {
    moveTo(58f, 46f)
    quadraticBezierTo(58f, 8f, 100f, 8f)
    quadraticBezierTo(142f, 8f, 142f, 46f)
    lineTo(139f, 84f)
    quadraticBezierTo(100f, 92f, 61f, 84f)
    close()
}

private fun eduFrontTooth(cx: Float, half: Float) = Path().apply {
    moveTo(cx - half, 30f)
    lineTo(cx + half, 30f)
    lineTo(cx + half - 1f, 96f)
    quadraticBezierTo(cx + half - 1f, 106f, cx + half - 7f, 106f)
    lineTo(cx - half + 7f, 106f)
    quadraticBezierTo(cx - half + 1f, 106f, cx - half + 1f, 96f)
    close()
}

private fun DrawScope.eduBone(top: Float) {
    drawRect(EduArt.bone, Offset(-300f, top), Size(800f, 300f))
    drawLine(EduArt.boneEdge, Offset(-300f, top), Offset(500f, top), strokeWidth = 2f)
}

private fun DrawScope.eduSimpleMolar() {
    drawPath(eduMolar(), EduArt.tooth)
    drawPath(eduPulp(), EduArt.pulp)
    drawPath(eduMolar(), EduArt.line, style = Stroke(2f))
}

private fun DrawScope.eduCrownCap() {
    val cap = eduCap()
    drawPath(cap, EduArt.crown)
    drawPath(cap, EduArt.crownLine, style = Stroke(2f))
    drawLine(Color.White, Offset(72f, 26f), Offset(86f, 18f), strokeWidth = 3f, cap = StrokeCap.Round)
}

private fun DrawScope.eduArrowUp(x: Float, y: Float, len: Float) {
    drawLine(EduArt.metalDark, Offset(x, y), Offset(x, y - len), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(EduArt.metalDark, Offset(x, y - len), Offset(x - 7f, y - len + 8f), strokeWidth = 3f, cap = StrokeCap.Round)
    drawLine(EduArt.metalDark, Offset(x, y - len), Offset(x + 7f, y - len + 8f), strokeWidth = 3f, cap = StrokeCap.Round)
}

private fun DrawScope.eduGumGap(sore: Boolean) {
    val p = Path().apply {
        moveTo(-300f, 90f)
        lineTo(76f, 90f)
        quadraticBezierTo(100f, 99f, 124f, 90f)
        lineTo(500f, 90f)
        lineTo(500f, 106f)
        lineTo(-300f, 106f)
        close()
    }
    drawPath(p, if (sore) EduArt.gumSore else EduArt.gum)
}

private fun DrawScope.eduGumSides(sc: EduScene) {
    val top = if (sc.boneLoss) 108f else 90f
    val bottom = if (sc.boneLoss) 124f else 106f
    val color = if (sc.sore) EduArt.gumSore else EduArt.gum
    val puff = if (sc.sore) 6f else 0f
    val leftEdge = if (sc.boneLoss) 67f else 65f
    val rightEdge = 200f - leftEdge
    val left = Path().apply {
        moveTo(-300f, top)
        lineTo(leftEdge - 11f, top)
        quadraticBezierTo(leftEdge + puff, top - puff, leftEdge + puff * 0.4f, top + 8f)
        lineTo(leftEdge, bottom)
        lineTo(-300f, bottom)
        close()
    }
    val right = Path().apply {
        moveTo(500f, top)
        lineTo(rightEdge + 11f, top)
        quadraticBezierTo(rightEdge - puff, top - puff, rightEdge - puff * 0.4f, top + 8f)
        lineTo(rightEdge, bottom)
        lineTo(500f, bottom)
        close()
    }
    drawPath(left, color)
    drawPath(right, color)
    if (sc.boneLoss) {
        val pocket = EduArt.red.copy(alpha = 0.55f)
        drawPath(Path().apply {
            moveTo(leftEdge - 4f, top + 2f)
            lineTo(leftEdge + 1f, top + 2f)
            lineTo(leftEdge + 2f, 120f)
            close()
        }, pocket)
        drawPath(Path().apply {
            moveTo(rightEdge + 4f, top + 2f)
            lineTo(rightEdge - 1f, top + 2f)
            lineTo(rightEdge - 2f, 120f)
            close()
        }, pocket)
    }
    if (sc.tartar) {
        listOf(
            Offset(leftEdge - 1f, top - 2f), Offset(leftEdge + 1f, top - 7f),
            Offset(rightEdge + 1f, top - 2f), Offset(rightEdge - 1f, top - 7f)
        ).forEach { drawCircle(EduArt.tartar, 3.5f, it) }
    }
}

private fun DrawScope.eduMolarScene(sc: EduScene) {
    eduBone(if (sc.boneLoss) 118f else 100f)
    if (sc.socket) {
        drawPath(eduRoots(), EduArt.boneEdge, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))))
        eduGumGap(false)
        return
    }
    translate(0f, -sc.lift) {
        val body = if (sc.stump) eduStump() else eduMolar()
        drawPath(body, EduArt.tooth)
        drawPath(
            eduPulp(),
            when (sc.pulp) {
                1 -> EduArt.pulpBad
                2 -> EduArt.rootFill
                else -> EduArt.pulp
            }
        )
        if (sc.pulp == 2) drawPath(eduChamber(), EduArt.core)
        when {
            sc.filling && !sc.stump -> {
                drawPath(eduHole(), EduArt.filling)
                drawPath(eduHole(), EduArt.line, style = Stroke(1.5f))
            }
            sc.hole -> {
                drawPath(eduHole(), EduArt.panel)
                drawPath(eduHole(), EduArt.line, style = Stroke(1.5f))
            }
            sc.decay > 0 -> drawPath(eduDecay(sc.decay), EduArt.decay)
        }
        drawPath(body, EduArt.line, style = Stroke(2f))
        if (sc.stump && !sc.crown) {
            drawPath(eduCap(), EduArt.crownLine, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
        }
        if (sc.broken) {
            drawPath(eduBrokenPart(), EduArt.panel)
            drawPath(eduCrack(), EduArt.decay, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        if (sc.crown) eduCrownCap()
    }
    if (sc.abscess) {
        drawCircle(EduArt.red.copy(alpha = 0.22f), 15f, Offset(106f, 150f))
        drawCircle(EduArt.red.copy(alpha = 0.5f), 6f, Offset(106f, 150f))
    }
    eduGumSides(sc)
    if (sc.lift > 0f) eduArrowUp(172f, 78f, 34f)
}

private fun DrawScope.eduImplantScene(sc: EduScene) {
    val dip = if (sc.implant == 0) 10f else 0f
    drawPath(Path().apply {
        moveTo(-300f, 100f)
        lineTo(66f, 100f)
        quadraticBezierTo(100f, 100f + dip * 2f, 134f, 100f)
        lineTo(500f, 100f)
        lineTo(500f, 400f)
        lineTo(-300f, 400f)
        close()
    }, EduArt.bone)
    val tilt = if (sc.implant == 0) 7f else 0f
    rotate(tilt, Offset(8f, 125f)) { translate(-92f, 0f) { eduSimpleMolar() } }
    rotate(-tilt, Offset(192f, 125f)) { translate(92f, 0f) { eduSimpleMolar() } }
    if (sc.implant >= 1) {
        if (sc.implant >= 2) drawRoundRect(EduArt.boneEdge, Offset(85f, 97f), Size(30f, 60f), CornerRadius(9f))
        drawRoundRect(EduArt.metal, Offset(91f, 100f), Size(18f, 52f), CornerRadius(6f))
        for (k in 0 until 6) {
            val y = 108f + k * 7.5f
            drawLine(EduArt.metalDark, Offset(91f, y), Offset(109f, y - 3f), strokeWidth = 1.5f)
        }
    }
    eduGumGap(false)
    if (sc.implant >= 3) {
        drawRect(EduArt.metal, Offset(94f, 80f), Size(12f, 18f))
        val cap = Path().apply {
            moveTo(64f, 48f)
            quadraticBezierTo(64f, 18f, 100f, 18f)
            quadraticBezierTo(136f, 18f, 136f, 48f)
            lineTo(131f, 84f)
            quadraticBezierTo(100f, 90f, 69f, 84f)
            close()
        }
        drawPath(cap, EduArt.crown)
        drawPath(cap, EduArt.crownLine, style = Stroke(2f))
        drawLine(Color.White, Offset(76f, 32f), Offset(88f, 25f), strokeWidth = 3f, cap = StrokeCap.Round)
    } else if (sc.implant >= 1) {
        drawRoundRect(EduArt.metalDark, Offset(93f, 86f), Size(14f, 8f), CornerRadius(3f))
    }
}

private fun DrawScope.eduFrontScene(sc: EduScene) {
    translate(0f, 18f) {
        val f = when (sc.braces) {
            0, 1 -> 1f
            2 -> 0.35f
            else -> 0f
        }
        val offs = listOf(
            Triple(-3f, 6f, -12f), Triple(4f, -3f, 10f), Triple(0f, 5f, -7f),
            Triple(2f, -4f, 9f), Triple(-4f, 4f, -9f), Triple(1f, 6f, 8f)
        )
        val xs = listOf(36f, 62f, 88f, 112f, 138f, 164f)
        val centers = mutableListOf<Offset>()
        xs.forEachIndexed { i, x ->
            val (dx, dy, deg) = offs[i]
            val cx = x + dx * f
            val cy = dy * f
            val half = if (i == 0 || i == 5) 10.5f else 12f
            rotate(deg * f, Offset(cx, 70f + cy)) {
                translate(0f, cy) {
                    val p = eduFrontTooth(cx, half)
                    drawPath(p, EduArt.tooth)
                    drawPath(p, EduArt.line, style = Stroke(1.8f))
                }
            }
            centers.add(Offset(cx, 72f + cy))
        }
        drawRect(EduArt.gum, Offset(-300f, -100f), Size(800f, 136f))
        xs.forEachIndexed { i, x ->
            val (dx, dy, _) = offs[i]
            drawOval(EduArt.gum, Offset(x + dx * f - 13f, 30f + dy * f), Size(26f, 14f))
        }
        if (sc.braces in 1..2) {
            centers.forEach { c ->
                drawRoundRect(EduArt.metal, Offset(c.x - 5.5f, c.y - 5f), Size(11f, 10f), CornerRadius(2f))
                drawRoundRect(EduArt.metalDark, Offset(c.x - 5.5f, c.y - 5f), Size(11f, 10f), CornerRadius(2f), style = Stroke(1f))
            }
            val wire = Path().apply {
                moveTo(centers.first().x - 14f, centers.first().y)
                centers.forEach { lineTo(it.x, it.y) }
                lineTo(centers.last().x + 14f, centers.last().y)
            }
            drawPath(wire, EduArt.metalDark, style = Stroke(1.8f, join = StrokeJoin.Round))
        }
        if (sc.braces == 3) {
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(20f, 40f), Size(160f, 70f), CornerRadius(20f))
            drawRoundRect(Color(0xFF8FB8DE), Offset(20f, 40f), Size(160f, 70f), CornerRadius(20f), style = Stroke(1.5f))
        }
    }
}

private fun DrawScope.eduWisdomScene(sc: EduScene) {
    eduBone(100f)
    withTransform({
        translate(-58f, 6f)
        scale(0.72f, 0.72f, Offset(100f, 100f))
    }) { eduSimpleMolar() }
    withTransform({
        translate(4f, 6f)
        scale(0.72f, 0.72f, Offset(100f, 100f))
    }) {
        eduSimpleMolar()
        if (sc.wisdom == 1) {
            drawPath(Path().apply {
                moveTo(136f, 44f)
                quadraticBezierTo(144f, 56f, 138f, 72f)
                lineTo(128f, 64f)
                close()
            }, EduArt.decay)
        }
    }
    if (sc.wisdom < 2) {
        withTransform({
            translate(70f, 40f)
            rotate(-40f, Offset(100f, 90f))
            scale(0.68f, 0.68f, Offset(100f, 100f))
        }) { eduSimpleMolar() }
    } else {
        drawOval(
            EduArt.boneEdge, Offset(146f, 106f), Size(38f, 32f),
            style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
        )
    }
    drawRect(if (sc.wisdom == 1) EduArt.gumSore else EduArt.gum, Offset(-300f, 92f), Size(800f, 14f))
    if (sc.wisdom == 1) {
        drawOval(EduArt.gumSore, Offset(128f, 82f), Size(62f, 22f))
        listOf(
            Offset(160f, 66f) to Offset(154f, 56f),
            Offset(172f, 62f) to Offset(172f, 50f),
            Offset(184f, 66f) to Offset(190f, 56f)
        ).forEach { (a, b) -> drawLine(EduArt.red, a, b, strokeWidth = 2.5f, cap = StrokeCap.Round) }
    }
}

private fun DrawScope.drawEduScene(sc: EduScene) {
    when (sc.kind) {
        1 -> eduImplantScene(sc)
        2 -> eduFrontScene(sc)
        3 -> eduWisdomScene(sc)
        else -> eduMolarScene(sc)
    }
}

@Composable
private fun EduPicture(scene: EduScene, modifier: Modifier) {
    Canvas(modifier) {
        val s = min(size.width / 200f, size.height / 170f)
        val ox = (size.width - 200f * s) / 2f
        val oy = (size.height - 170f * s) / 2f
        withTransform({
            translate(ox, oy)
            scale(s, s, Offset.Zero)
        }) {
            drawEduScene(scene)
        }
    }
}

private fun eduWhatsApp(context: Context, phone: String, text: String): Boolean {
    val ten = phone.filter { it.isDigit() }.takeLast(10)
    val intent = if (ten.length == 10) {
        Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/91$ten?text=" + Uri.encode(text)))
    } else {
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Send"
        )
    }
    return runCatching { context.startActivity(intent) }.isSuccess
}

// ───────────── Card in the patient file ─────────────
@Composable
fun EducationCard(db: ClinicDb, patientId: Long) {
    var open by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth(), onClick = { open = true }, padding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Explain to patient", fontFamily = DisplayFont, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Brand.Ink)
                Text("Pictures and simple words · English, हिंदी, मराठी", fontSize = 12.sp, color = Brand.Sub)
            }
            Spacer(Modifier.width(8.dp))
            SoftButton("Open", null, height = 40.dp) { open = true }
        }
    }
    if (open) EducationScreen(db, patientId) { open = false }
}

// ───────────── The library ─────────────
@Composable
private fun EducationScreen(db: ClinicDb, patientId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("education", Context.MODE_PRIVATE) }
    var lang by remember { mutableIntStateOf(prefs.getInt("lang", 0)) }
    val patient = remember { db.getPatient(patientId) }
    val clinicName = remember { db.clinicInfo().name }
    val suggested = remember {
        val names = db.treatmentsOf(patientId).filter { it.status != STATUS_DONE }.map { it.name.lowercase() }
        EDU_TOPICS.filter { t -> names.any { n -> t.keys.any { k -> k in n } } }.map { it.id }.toSet()
    }
    val topics = remember(suggested) { EDU_TOPICS.sortedBy { if (it.id in suggested) 0 else 1 } }
    var topicId by remember { mutableStateOf(topics.first().id) }
    val topic = EDU_TOPICS.first { it.id == topicId }

    fun setLang(l: Int) {
        lang = l
        prefs.edit().putInt("lang", l).apply()
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, onClose, bg = Brand.Surface, tint = Brand.Ink, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Explain to patient", fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Brand.Ink)
                    Text("Works without internet", fontSize = 12.sp, color = Brand.Sub)
                }
            }
            Column(
                Modifier.widthIn(max = 900.dp).fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("English", lang == 0) { setLang(0) }
                    ChoiceChip("हिंदी", lang == 1) { setLang(1) }
                    ChoiceChip("मराठी", lang == 2) { setLang(2) }
                }
                topics.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { t ->
                            EduTile(t, lang, t.id == topicId, t.id in suggested, Modifier.weight(1f)) { topicId = t.id }
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (suggested.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Brand.Gold))
                        Spacer(Modifier.width(6.dp))
                        Text("Matches this patient's treatments", fontSize = 12.sp, color = Brand.Sub)
                    }
                }
                key(topicId) {
                    EduTopicPanel(topic, lang, patient?.name.orEmpty(), patient?.phone.orEmpty(), clinicName)
                }
            }
        }
    }
}

@Composable
private fun EduTile(t: EduTopic, lang: Int, selected: Boolean, suggested: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.clip(shape).background(if (selected) Brand.GreenSoft else Brand.Surface)
            .border(if (selected) 1.5.dp else 0.5.dp, if (selected) Brand.GreenDeep else Brand.Line, shape)
            .clickable(onClick = onClick).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(10.dp)).background(EduArt.panel)) {
            EduPicture(t.steps[t.thumb].scene, Modifier.fillMaxSize().padding(2.dp))
            if (suggested) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(9.dp).clip(CircleShape).background(Brand.Gold))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            t.title(lang), fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 2,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) Brand.GreenText else Brand.Ink
        )
    }
}

@Composable
private fun EduTopicPanel(topic: EduTopic, lang: Int, patientName: String, phone: String, clinicName: String) {
    val context = LocalContext.current
    val notify = LocalNotify.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { topic.steps.size })
    var drawMode by remember { mutableStateOf(false) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val page = pager.currentPage

    LaunchedEffect(page) {
        strokes.clear()
        live = emptyList()
    }

    fun send() {
        val first = patientName.trim().substringBefore(" ")
        val greet = when (lang) {
            1 -> "नमस्ते $first, आज हमने जिसके बारे में बात की, उसकी जानकारी:"
            2 -> "नमस्कार $first, आज आपण ज्याबद्दल बोललो त्याची माहिती:"
            else -> "Hello $first, here is the information we discussed today:"
        }
        val body = topic.steps.mapIndexed { i, s -> "${i + 1}. ${s.text(lang)}" }.joinToString("\n")
        val text = "$greet\n\n*${topic.title(lang)}*\n$body\n\n— $clinicName"
        if (!eduWhatsApp(context, phone, text)) notify("Could not open WhatsApp")
    }

    AppCard(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(topic.title(lang), fontFamily = DisplayFont, fontFamily = DisplayFont, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Brand.Ink, modifier = Modifier.weight(1f))
            Text("Step ${page + 1} of ${topic.steps.size}", fontSize = 12.sp, color = Brand.Sub)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(20.dp)).background(EduArt.panel)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = !drawMode) { p ->
                EduPicture(topic.steps[p].scene, Modifier.fillMaxSize().padding(10.dp))
            }
            Canvas(
                Modifier.matchParentSize().pointerInput(drawMode) {
                    if (!drawMode) return@pointerInput
                    detectDragGestures(
                        onDragStart = { live = listOf(it) },
                        onDragEnd = {
                            if (live.size > 1) strokes.add(live)
                            live = emptyList()
                        },
                        onDragCancel = { live = emptyList() },
                        onDrag = { change, _ ->
                            change.consume()
                            live = live + change.position
                        }
                    )
                }
            ) {
                (strokes + listOf(live)).forEach { pts ->
                    if (pts.size > 1) {
                        val p = Path().apply {
                            moveTo(pts[0].x, pts[0].y)
                            for (k in 1 until pts.size) lineTo(pts[k].x, pts[k].y)
                        }
                        drawPath(p, EduArt.red, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            if (drawMode) {
                Text(
                    "Drawing", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(10.dp))
                        .background(EduArt.red).padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            if (strokes.isNotEmpty()) {
                Text(
                    "Clear", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = EduArt.metalDark,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color.White).clickable { strokes.clear() }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(topic.steps[page].text(lang), fontSize = 17.sp, lineHeight = 25.sp, color = Brand.Ink, minLines = 3)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(topic.steps.size) { i ->
                Box(
                    Modifier.padding(3.dp).height(7.dp).width(if (i == page) 20.dp else 7.dp)
                        .clip(RoundedCornerShape(50)).background(if (i == page) Brand.Gold else Brand.Line)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircleIconButton(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                { scope.launch { pager.animateScrollToPage((page - 1).coerceAtLeast(0)) } },
                bg = Brand.Soft, tint = Brand.Ink, size = 46.dp
            )
            SoftButton(if (drawMode) "Done" else "Draw", Icons.Filled.Edit, Modifier.weight(1f), height = 46.dp) {
                drawMode = !drawMode
            }
            GradientButton("Send", Modifier.weight(1f), Icons.AutoMirrored.Filled.Send, height = 46.dp) { send() }
            CircleIconButton(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                { scope.launch { pager.animateScrollToPage((page + 1).coerceAtMost(topic.steps.size - 1)) } },
                bg = Brand.GreenDeep, tint = Color.White, size = 46.dp
            )
        }
    }
}
