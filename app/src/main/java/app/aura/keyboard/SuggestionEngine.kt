package app.aura.keyboard

/** Word completion from a built-in word list plus words you type often. Nothing leaves the device. */
class SuggestionEngine(private val prefs: Prefs) {
    private val user = HashMap<String, Int>()

    init {
        load()
    }

    fun load() {
        user.clear()
        for (line in prefs.userWords.split('\n')) {
            val t = line.split('\t')
            if (t.size == 2 && t[0].isNotEmpty()) {
                val c = t[1].toIntOrNull()
                if (c != null) user[t[0]] = c
            }
        }
    }

    private fun save() {
        prefs.userWords = user.entries
            .sortedByDescending { it.value }
            .take(2000)
            .joinToString("\n") { it.key + "\t" + it.value }
    }

    fun learn(word: String) {
        val w = word.lowercase()
        if (w.length < 3) return
        if (!w.all { it.isLetter() || it == '\'' }) return
        if (BASE_SET.contains(w)) return
        user[w] = (user[w] ?: 0) + 1
        save()
    }

    fun suggest(prefix: String): List<String> {
        val p = prefix.lowercase()
        val out = ArrayList<String>(3)
        val mine = user.entries
            .filter { it.key.length > p.length && it.key.startsWith(p) }
            .sortedByDescending { it.value }
        for (e in mine) {
            if (out.size < 3 && e.value >= 2) out.add(e.key)
        }
        for (w in WORDS) {
            if (out.size >= 3) break
            if (w.length > p.length && w.startsWith(p) && !out.contains(w)) out.add(w)
        }
        for (e in mine) {
            if (out.size >= 3) break
            if (!out.contains(e.key)) out.add(e.key)
        }
        if (out.isEmpty()) out.add(prefix)
        return out
    }

    companion object {
        private val WORDS: List<String> = (
            "the be to of and a in that have i it for not on with he as you do at this but his by from they we say her she or an " +
            "will my one all would there their what so up out if about who get which go me when make can like time no just him know " +
            "take people into year your good some could them see other than then now look only come its over think also back after " +
            "use two how our work first well way even new want because any these give day most us is was are were been has had did " +
            "does said made went got let put end why ask men read need land different home move try kind hand picture again change " +
            "off play spell air away animal house point page letter mother answer found study still learn should world high every " +
            "near add food between own below country plant last school father keep tree never start city earth eye light thought " +
            "head under story saw left don't few while along might close something seem next hard open example begin life always " +
            "those both paper together group often run important until children side feet car mile night walk white sea began grow " +
            "took river four carry state once book hear stop without second later miss idea enough eat face watch far real almost " +
            "above girl sometimes mountain cut young talk soon list song being leave family it's body music color stand sun " +
            "questions fish area mark dog horse birds problem complete room knew since ever piece told usually friends easy heard " +
            "order red door sure become top ship across today during short better best however low hours black products happened " +
            "whole measure remember early waves reached listen wind rock space covered fast several hold himself toward five step " +
            "morning passed true hundred against pattern table north slowly money map farm pulled draw voice seen cold cried plan " +
            "notice south sing war ground fall king town i'll unit figure certain field travel wood fire upon done english road " +
            "half ten fly gave box finally wait correct oh quickly person became shown minutes strong stars front feel fact " +
            "street decided contain course surface produce building ocean class note nothing rest carefully inside stay green " +
            "known island week less machine base ago stood plane system behind ran round boat game force brought understand warm " +
            "common bring explain dry though language shape deep thousands yes clear yet government filled heat full hot check " +
            "object am rule among power cannot able six size dark ball material special heavy fine pair circle include built " +
            "can't matter square perhaps felt suddenly test direction center ready anything divided general energy subject " +
            "region return believe dance members picked simple paint mind love cause rain exercise train blue wish drop " +
            "developed window difference distance heart sit summer wall forest probably legs sat main winter wide written length " +
            "reason kept interest arms brother race present beautiful store job edge past sign record finished discovered wild " +
            "happy beside gone sky glass million west lay weather root meet third months raised represent soft whether clothes " +
            "flowers shall teacher held describe drive cross speak solve appear metal son either ice sleep village result " +
            "jumped snow ride care floor hill pushed baby buy century outside everything tall already instead soil bed copy " +
            "free hope spring case laughed nation quite type themselves temperature bright lead everyone method section lake " +
            "within hair age amount scale although per broken moment tiny possible gold milk quiet natural lot stone act " +
            "build middle speed count cat someone sail rolled bear wonder smiled trip hole poor let's fight surprise died " +
            "beat exactly remain dress iron couple steel thanks please hello hey okay sorry thank tomorrow tonight message " +
            "call meeting email phone number address password account yesterday where here really actually definitely " +
            "maybe awesome amazing great nice cool bro dude lol birthday congratulations welcome yeah yep nope wanna gonna " +
            "gotta i'm i've you're we're they're isn't didn't doesn't won't wouldn't couldn't shouldn't that's what's there's " +
            "he's she's wasn't aren't haven't hasn't hadn't weren't " +
            "kya hai nahi accha theek bhai kaise kab kahan aur mein haan bahut abhi chalo baat kaam ghar dost paisa jaldi " +
            "thoda sab kuch mujhe tum aap kal aaj raat subah"
            ).split(' ').filter { it.isNotEmpty() }.distinct()

        private val BASE_SET: HashSet<String> = HashSet(WORDS)
    }
}
