#!/usr/bin/env python3
# Builds ../evidence/maintainer-quotes.md: a curated, ID-numbered register of the maintainer's own words.
#
# Every entry is VERIFIED against ../evidence/maintainer-messages.txt: the excerpt must occur verbatim in a
# message with that timestamp prefix and session prefix, or the script fails. Typos are the maintainer's
# and are kept. "..." inside an excerpt is the maintainer's own punctuation, never an elision by us.
#
# Usage: python3 extract_maintainer_messages.py first, then python3 build_quotes.py

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
EVIDENCE = os.path.join(HERE, "..", "evidence")

# (id, topic, utc timestamp prefix, session prefix, verbatim excerpt)
QUOTES = [
    # --- P: process, role, review regime ---
    ("P1", "process", "2026-08-19T08:29", "33180650",
     "This is the core of everything and the design and implementation goals are:\n1. it must work as intended\n2. it must be easy to follow along the code of the eq and the graph optimizer\n3. performance must improve"),
    ("P2", "process", "2026-08-19T08:29", "33180650",
     "Why? Especially 1. and 2. are important as some day we want to port the audio-backend to zig. So the more concrete, correct and conscise comments (CCC-rule ... just made this up :) ) and code paths we have the easier the port will be."),
    ("P3", "process", "2026-08-27T11:17", "e42d2d00",
     "whenever you are done with one portion, DO NOT commit yet. I will check git status then and have a look at the changes."),
    ("P4", "process", "2026-08-28T12:13", "e42d2d00",
     "We are now in the hard phase, so gradual improvements are good now. Everythings was \"written in a hurry\"... very good when we discover these mistakes now and address one by one."),
    ("P5", "process", "2026-08-28T13:44", "e42d2d00",
     "Whenever you have a stable point come back to me so that i can verify or do a listening test"),
    ("P6", "process", "2026-08-28T14:39", "e42d2d00",
     "I have the feeling that the reviewers sometimes create more trouble than order"),
    ("P7", "process", "2026-08-28T14:42", "e42d2d00",
     "First round is blind with task description and change-set. Every consequetive round needs to include the previous review result and the resulting changes from that."),
    ("P8", "process", "2026-08-28T14:59", "e42d2d00",
     "I would say audio backend, bridge and wireformat and everything else related to the core needs to be mutation tested. On other parts we might not need to be so strict."),
    ("P9", "process", "2026-08-28T15:10", "e42d2d00",
     "I think there is also no need for preset pins ... this is just writing the same code twice, which has no value"),
    ("P10", "process", "2026-08-29T21:51", "127f0cb2",
     "this is prototyping so no need for reviews"),
    ("P11", "process", "2026-09-06T16:04", "743ece1a",
     "Rules need a \"hardness\" describing how binding a rule is, whether it is set into stone or if it is a guideline / guardrail. I do not want you to have to work with un-reasonable restrictions. And on top of this: not everything that i say is to be treated as a forever rule..."),
    ("P12", "process", "2026-09-05T20:34", "743ece1a",
     "Make sure to keep all memories here in the repos skills, not in my home-dir please"),
    ("P13", "process", "2026-09-18T15:02", "2b9d5146",
     "I have the impression that we are \"tieing knots\" arround our legs with some of the safety nets ... So every now and then some cleanup is advisable to keep the pace high."),
    ("P14", "process", "2026-09-19T09:24", "2b9d5146",
     "Because remember, the real \"enemy\" is complexity here."),
    ("P15", "process", "2026-09-15T17:16", "2b9d5146",
     "This also seems to be a good instance for TDD, especially for the optimizer. We construct the cases for which we expect optimizations to happen upfront."),
    ("P16", "process", "2026-09-18T07:54", "2b9d5146",
     "Every agent (coder, reviewer, audio-engineer, strategiest, tester, etc...) needs to hear that they are world-class great at what they are doing."),
    ("P17", "process", "2026-09-18T07:55", "2b9d5146",
     "We need to monitor this a bit ... not sure if this works, this is just a theory, might also tip agents into the \"arrogance\" area ..."),

    # --- A: AI use, model allocation (for the disclosure section) ---
    ("A1", "ai-use", "2026-08-15T16:21", "eec0ca94",
     "Which models would you use for writing the tutorials? I would tend to Fable 5 as this is user facing and important content."),
    ("A2", "ai-use", "2026-09-08T13:54", "e42d2d00",
     "I will let Fable do the parser part. The implementation of stdlib function can then be done by Opus again."),
    ("A3", "ai-use", "2026-09-16T06:59", "2b9d5146",
     "The writing must be done by Fable, as it is the one currently outputting the most appealing language."),
    ("A4", "ai-use", "2026-08-15T15:28", "eec0ca94",
     "Some / most of them feel like ai-slop somehow."),
    ("A5", "ai-use", "2026-09-10T08:57", "9f5d38a5",
     "We now have > 430.000 LoC in Kotlin in this project... how much this worth, would a company have built it?"),

    # --- K: KlangScript ---
    ("K1", "klangscript", "2026-09-05T18:54", "743ece1a",
     "So basically ... i am thinking about moving KlangScript away from being a Javascript dialect to it being a kotlin dialect ... What do you think?"),
    ("K2", "klangscript", "2026-09-05T19:06", "743ece1a",
     "So maybe introducing the config lambdas everywhere is the more pragmatic way to go for V1."),
    ("K3", "klangscript", "2026-09-06T16:51", "743ece1a",
     "Yes the kotlin-dialect is a very far into future things ... maybe we would call this KlangScript2 ... i think we do not even need to keep this at all. The current dsl surface is ok."),
    ("K4", "klangscript", "2026-08-23T16:29", "8b490939",
     "KlangScript forces all params to be named or none. There is no mix of positional and named params"),
    ("K5", "klangscript", "2026-09-07T11:48", "743ece1a",
     "Because with a dedicated annotation we could raise an error when an object has more than one of those, as KlangScript does not support overloads."),
    ("K6", "klangscript", "2026-09-08T13:51", "e42d2d00",
     "Ok on the power operator ... I think we will not do this, so mark this one as won't implement please."),
    ("K7", "klangscript", "2026-09-09T09:32", "e42d2d00",
     "So yeah, i decided that the ^ operator will stay bitwise XOR."),
    ("K8", "klangscript", "2026-09-09T09:38", "ffb74c9a",
     "Yeah and the numbers and variable behave differently is a design error... so we need to switch this back to be on par with kotlin."),
    ("K9", "klangscript", "2026-09-10T09:53", "9f5d38a5",
     "to me as a human this: a.max(3) ... reads as: take \"a\" but at max 3 ... not as: take the max of a and 3."),
    ("K10", "klangscript", "2026-09-08T19:24", "ffb74c9a",
     "should KlangScript which is used for live music coding throw runtime errors, or should functions like modulo or the clamp handle errors gracefully."),
    ("K11", "klangscript", "2026-08-20T14:05", "33180650",
     "The foundation of all DSL should be so that they can also be used from kotlin directly. We managed to achieve this for sprudel."),
    ("K12", "klangscript", "2026-09-18T15:16", "2b9d5146",
     "Exactly we need a way to express a union type, which would also be beneficial for PatternLike and friends."),

    # --- S: Sprudel vs Strudel ---
    ("S1", "sprudel", "2026-08-23T14:14", "8b490939",
     "The current shape of the filters \"happened\" because this project was copying strudel.cc . Now we have grown out of this and need to build the good/correct way."),
    ("S2", "sprudel", "2026-08-23T14:14", "8b490939",
     "The overacrching principle must be, that the same \"thing\" must be have the same \"way\" no matter in which context it is used. We owe this to the user, as the whole project has already quite a level of complexity."),
    ("S3", "sprudel", "2026-08-23T14:36", "8b490939",
     "We will fully abondon the heritage of strudel here. We have the compatibilty test between sprudel and strudel. We need to cut these down to only structural functions. Setting simple fields on a voice does not yield any benefit anymore in the compat tests."),
    ("S4", "sprudel", "2026-08-23T14:36", "8b490939",
     "For us it is now the time and chance to fix the structural inconsistencies in strudel... we can do what ever we want, no compat at.. pre-alpha..."),
    ("S5", "sprudel", "2026-08-23T14:50", "8b490939",
     "Strudel tried to stay compatible with Tidal, okay ... but this will just produce a mess. We clean this up now."),
    ("S6", "sprudel", "2026-08-23T15:30", "8b490939",
     "The dsl is NOT ordered, except for the structural functions. Which in itself is a design flaw, but we will not fix it here!"),
    ("S7", "sprudel", "2026-08-23T16:29", "8b490939",
     "Or: we drop the compound form completely. This is also inherited from strudel, due to how they define functions and how they are forced to parse params ..."),
    ("S8", "sprudel", "2026-08-28T19:17", "928ac1d3",
     "1. double -> drifted and rounding errors and therefore event fetching error\n2. Rational impl 1 -> kind of fixed floating point\n3. Rational impl 2\n4. CycleTime"),
    ("S9", "sprudel", "2026-08-30T19:54", "c7c72e06",
     "Well applying the mods at parse time has the downside that every other function coming later in the pattern chain would override the settings applied by the mod."),
    ("S10", "sprudel", "2026-09-05T20:35", "743ece1a",
     "The sprudel dsl works like this as well at construction time. But it uses mutable objects at runtime. This is a deliberate runtime optimization"),
    ("S11", "sprudel", "2026-09-06T18:22", "743ece1a",
     "Hmmm, this is copying the ctx on every control event, which will be quite a performance hit. I think we should not do this. We would build an expensive \"thing\" for only one special usage."),
    ("S12", "sprudel", "2026-09-07T09:02", "743ece1a",
     "I do not want to pollute the namespace with many accessors"),
    ("S13", "sprudel", "2026-09-07T11:16", "743ece1a",
     "I like this approach as it cleans up the global sprudel namespace in KlangScript"),
    ("S14", "sprudel", "2026-09-07T13:24", "f6fba18e",
     "Now our sprudel impl has diverged significantly from Strudel. So the whole addons concept does not even make sense anymore."),
    ("S15", "sprudel", "2026-09-16T16:35", "351cbd90",
     "Hmm i would argue that a rest in a control pattern should have the meaning of \"leave untouched\"."),
    ("S16", "sprudel", "2026-09-17T15:09", "2b9d5146",
     "So in general every pattern kind must be able to wrap every other pattern kind to achieve full interoparability in all directions."),

    # --- M: Motor / engine ---
    ("M1", "motor", "2026-08-23T16:02", "8b490939",
     "Sprudel should not get it's own EQ impl. See sprudel as just one frontend driving the audio engine. It is only here \"by accident\". The engine comes first, it is the horse, and sprudel rides that horse, not the other way arround."),
    ("M2", "motor", "2026-08-27T10:25", "e42d2d00",
     "If there is noise without envelope then it is what it is. Engine stays raw, and when the user wants to express this they can."),
    ("M3", "motor", "2026-08-27T11:11", "e42d2d00",
     "I do not like that we make the context nullable to achieve this one goal. This will degrade the rest."),
    ("M4", "motor", "2026-08-28T12:32", "e42d2d00",
     "Ok so the blockSize is curently always 128 but we have to treat it as none-constant. So all rendering MUST work with different sizes too."),
    ("M5", "motor", "2026-08-28T12:49", "e42d2d00",
     "What we want is: A. structurally correct at any block size ... not B."),
    ("M6", "motor", "2026-08-28T13:20", "e42d2d00",
     "So i would rather simplify the implementation of the dsp so it does not need to care about the late voices at all as a hard rule."),
    ("M7", "motor", "2026-08-28T13:35", "e42d2d00",
     "Currently we are mixing two problems, which leads to more complexity and bugs as we see. So let us split the two problems and solve them individually."),
    ("M8", "motor", "2026-08-28T20:48", "127f0cb2",
     "This is probably even better, because it splits the two concepts cleanly already on the wire"),
    ("M9", "motor", "2026-08-28T20:51", "127f0cb2",
     "I would rather not make the startTime nullable only to serve a \"special\" case..."),
    ("M10", "motor", "2026-08-30T12:15", "e42d2d00",
     "so I think we need two different concepts of frequencies. instrument/note frequency vs. lfos.\n\ndetune should only ever affect the musical frequencies."),
    ("M11", "motor", "2026-08-20T11:31", "33180650",
     "Then let us do exactly this: a seeded random per voice, which is anyway good if we want bit-identical reproduction capabilities."),
    ("M12", "motor", "2026-09-03T15:35", "e42d2d00",
     "Do we ever shring an already allocated buffer? I would not do this."),
    ("M13", "motor", "2026-09-03T15:51", "e42d2d00",
     "1. build the entire Warehouse in isolation and test it properly. \n2. Integrate it step by step into the dsp"),
    ("M14", "motor", "2026-09-04T07:02", "e42d2d00",
     "I would not use a builtin song for warmup. Let us do this explicitly."),
    ("M15", "motor", "2026-09-04T13:51", "e42d2d00",
     "If the resource is returned from the shelf and it is still dirty, it needs to be cleaned (zeroed) first."),
    ("M16", "motor", "2026-09-15T08:27", "9d12eb31",
     "Once we start this, i would like to only maintain one backend impl, meaning the kotlin impl would go away."),
    ("M17", "motor", "2026-09-15T08:38", "9d12eb31",
     "So in general, if possible, not strings should cross into the engine."),
    ("M18", "motor", "2026-09-17T16:05", "2b9d5146",
     "Basic rule: nothing in the be and fe must ever allocate without the chance to clean it up."),
    ("M19", "motor", "2026-09-18T07:34", "2b9d5146",
     "Ok switching stages on and off should always crossfade"),
    ("M20", "motor", "2026-09-18T07:41", "2b9d5146",
     "If we go for the states they should no be allocated in the hotloop. Every state class should have one instance created when the node is created."),
    ("M21", "motor", "2026-09-18T07:41", "2b9d5146",
     "Performance is the top priority, but complexity reduction and readability are very close second."),
    ("M22", "motor", "2026-09-18T07:45", "2b9d5146",
     "Exactly no framework. Full flexibility for each effect. The idea itself is enough of a framework already."),
    ("M23", "motor", "2026-09-19T09:22", "2b9d5146",
     "We only build a security net, not a sound-proof security net."),
    ("M24", "motor", "2026-09-20T11:51", "2b9d5146",
     "Ok so most things seem to be fine, except the morphing of the formants does not seem to be the correct approach. And it is clear why... moving the filter resonances and q creates an audible filter-sweep."),
    ("M25", "motor", "2026-09-20T12:00", "2b9d5146",
     "No cap, just one parking slot."),
    ("M26", "motor", "2026-09-20T12:04", "2b9d5146",
     "This also mean we can remove the bank cap completely, more raw motor."),
    ("M27", "motor", "2026-08-31T15:51", "127f0cb2",
     "So what we need is two backend channels: 1. Playback and 2. Interactive"),

    # --- D: the DSLs, parity, naming ---
    ("D1", "dsl", "2026-08-19T08:15", "33180650",
     "The underlying eq machinery must be reusable for the master dsl and the upcoming katalyst dsl."),
    ("D2", "dsl", "2026-08-23T14:36", "8b490939",
     "1. the q default must be the same everywhere: 0.707 ... we need to ensure this, as this can be learned with ease... one constant default everywhere is simple"),
    ("D3", "dsl", "2026-08-23T14:50", "8b490939",
     "Remember, the build in songs are never meant to be \"released\" like a recording. They are our test-bench. So if the sound changes due to engine changes, this is fine, we can re-adjust. The engine must be clean."),
    ("D4", "dsl", "2026-08-23T15:37", "8b490939",
     "I am looking for unifications whereever possible."),
    ("D5", "dsl", "2026-08-23T17:08", "8b490939",
     "Ok one more thing: let us remove klangblocks! So there is one surface less we need to care about"),
    ("D6", "dsl", "2026-08-24T07:24", "8b490939",
     "When it is about pitch we will use semitones now, whenever this makes sense."),
    ("D7", "dsl", "2026-08-25T06:53", "34da42ea",
     "I have decided to change the ö into an o in Klangmotör ... This is an early name, and somehow carries too much ego."),
    ("D8", "dsl", "2026-09-05T18:56", "743ece1a",
     "I am not happy with some of the things we do in the dsls, like f.e. the Osc.supersaw() ... i will give you Supersaw as the return value and you can call functions on it, but as soon as you call a function that is not on Supersaw but on Ignitor, you have broken the chain."),
    ("D9", "dsl", "2026-09-05T20:19", "743ece1a",
     "on 2. We should remove everything that is no longer needed. No backward compat necessary. Clean foundation is the goal."),
    ("D10", "dsl", "2026-09-05T20:19", "743ece1a",
     "The configure lambdas should work on a \"special\" builder type, so that calling the wrong dsl functions is not even possible"),
    ("D11", "dsl", "2026-09-05T20:31", "743ece1a",
     "This should be the general principle for all the DSLs. Everything is immutable. \"Mutating\" operation return a new instance with updated values."),
    ("D12", "dsl", "2026-09-05T20:32", "743ece1a",
     "just wanted to add to the immutability: why? It removes an entire bug-class, which means less explaining needs to be done"),
    ("D13", "dsl", "2026-09-16T13:10", "351cbd90",
     "And: since we have the raw-motor policy, is there a reason the clamp the size to max 10?"),
    ("D14", "dsl", "2026-09-16T14:58", "351cbd90",
     "the default must be the same on every surface ... which somehow call for constants."),
    ("D15", "dsl", "2026-09-17T13:38", "2b9d5146",
     "The current engine layout and even the EngineDsl is a direct heritage of porting strudel."),
    ("D16", "dsl", "2026-09-17T14:24", "2b9d5146",
     "1. Stage: cut down the voice data to the bare minimum.\n2. Stage: how do we get convenience back into the sprudel dsl"),
    ("D17", "dsl", "2026-09-18T05:07", "2b9d5146",
     "Hmm ... i think we might have made a design mistake here. Chaining the kat is error-prone."),
    ("D18", "dsl", "2026-09-18T20:47", "2b9d5146",
     "This seems wrong, as it introduces \"magic\", which we try to avoid"),
    ("D19", "dsl", "2026-09-18T21:03", "2b9d5146",
     "I am not a big fan of loud warnings and such. These are last resort solutions, and i would rather like to find constructions that are flexible and safe at the same time"),
    ("D20", "dsl", "2026-09-18T21:25", "2b9d5146",
     "I still do not fully understand why sound(Osc.saw()) would be equivalent to sound(\"saw\") ... i think it should not be"),
    ("D21", "dsl", "2026-09-18T21:44", "2b9d5146",
     "In this sense velocity will never hit the backend, we multiply it into gain on the frontend."),
    ("D22", "dsl", "2026-09-23T06:40", "2b9d5146",
     "So this new task is NOT only about getting it to work, which is a given. The evenly important part is to get the factoring right."),
    ("D23", "dsl", "2026-09-23T11:17", "2b9d5146",
     "It is important that we are consistent."),
    ("D24", "dsl", "2026-09-17T14:50", "2b9d5146",
     "We want to retire the PipelineDsl anyways now."),

    # --- SD: sound design, listening ---
    ("SD1", "sound", "2026-08-24T10:48", "8b490939",
     "Because the round-robbin creates audible patterns that sounds like gargling ... should only be opt in.\nThe most natural \"guitar like\" should be a normal distribution."),
    ("SD2", "sound", "2026-08-29T17:08", "127f0cb2",
     "We introduced them at a time when there were still many bugs in the dsp... kind of an accident and now these make the sound of the saw \"muffy\"... not bright."),
    ("SD3", "sound", "2026-09-14T19:19", "2b9d5146",
     "Ok this is like going into a parfume store ... after the first 5 i cannot tell any smell from the rest ... so same here ... i need to compare this to a fixed reference"),
    ("SD4", "sound", "2026-09-14T20:23", "2b9d5146",
     "the ever changing filter lead to a \"strange\" listening experience"),
    ("SD5", "sound", "2026-08-15T17:36", "eec0ca94",
     "The tutorials must all be of equal volume to not pierce the ears of viewers."),
    ("SD6", "sound", "2026-09-15T17:12", "2b9d5146",
     "Bit identity is not necessary as long as we are only off by a specified margin that has no musical meaning at all, which means being inaudiable in 99.9999% of cases."),
    ("SD7", "sound", "2026-09-20T11:51", "2b9d5146",
     "On Sc10 the body-woodglass-fast-morph creates something that sound like a 8-bit \"laser-shot\"."),

    # --- PF: performance ---
    ("PF1", "performance", "2026-08-19T12:25", "33180650",
     "So my assumption is that on the FF4 the caches are just too small."),
    ("PF2", "performance", "2026-08-19T15:06", "33180650",
     "half of Schmetterling is running again on my phone... when the lead enters it still stalls"),
    ("PF3", "performance", "2026-08-20T14:37", "33180650",
     "Ok great it works! Massive 33% reduction in cpu time on this machine."),
    ("PF4", "performance", "2026-08-20T18:47", "33180650",
     "So basically only direct neighbours of frequency filters can be grouped."),
    ("PF5", "performance", "2026-08-20T19:07", "33180650",
     "The optimizer does not need to be fully complete. It should cover the most common cases and this reliably."),
    ("PF6", "performance", "2026-08-31T14:21", "6e733561",
     "In german we say: Kleinvieh macht auch Mist."),
    ("PF7", "performance", "2026-09-04T06:54", "e42d2d00",
     "The first run of Schmetterling basically kills the playback. The allocation of the cylinders and the rings is too much for one frame..."),
    ("PF8", "performance", "2026-09-15T08:16", "2b9d5146",
     "Ok i think it would be nice to control the size of the culling window per voice"),
    ("PF9", "performance", "2026-09-16T06:20", "2b9d5146",
     "My intuition is to run the optimizer in rounds until no further optimization is found. Why? Because it keeps the logic of each rounds \"small\"."),
    ("PF10", "performance", "2026-09-16T06:31", "2b9d5146",
     "But i have the hunch that this introduces more complexity for little gain."),
    ("PF11", "performance", "2026-09-16T07:35", "2b9d5146",
     "The RTF on the phone is now worse than 2 weeks ago but f.e. the guitars got way more complex in their signal graph."),
    ("PF12", "performance", "2026-09-16T08:10", "2b9d5146",
     "Trying to define a true baseline can only fail and will lead to questionable posts."),
    ("PF13", "performance", "2026-09-15T12:15", "2b9d5146",
     "I am not interested in bit-identical behaviour, it is more about performance."),

    # --- F: federation, sharing, whitepaper ---
    ("F1", "federation", "2026-09-10T08:22", "9f5d38a5",
     "In the white paper in the section \"Open source, and the node idea\" let us add the current API design and endpoints briefly so it becomes more tangible for a reader."),
    ("F2", "federation", "2026-09-14T19:19", "2b9d5146",
     "we need the online portion of this project, so that I can put all the stages you just built into a separate \"song\" and export them from there"),
    ("F3", "federation", "2026-09-17T15:09", "2b9d5146",
     "A song will consist of multiple files that can be of multiple types: sprudel, launchpad, classic sheet music, guitar tab."),
    ("F4", "federation", "2026-08-28T15:24", "928ac1d3",
     "So to conclude: brevity is key. Bragging not a good thing. Too concrete of numbers not a good thing."),
]


def load_messages():
    path = os.path.join(EVIDENCE, "maintainer-messages.txt")
    text = open(path).read()
    parts = re.split(r"(?m)^=== ", text)[1:]
    msgs = []
    for p in parts:
        head, _, body = p.partition("\n")
        ts, sid = [x.strip() for x in head.split("|")]
        msgs.append((ts, sid, body.rstrip("\n")))
    return msgs


def main():
    msgs = load_messages()
    failed = []
    rows = []
    for qid, topic, ts, sid, excerpt in QUOTES:
        hit = next((m for m in msgs if m[0].startswith(ts) and m[1].startswith(sid) and excerpt in m[2]), None)
        if hit is None:
            failed.append(qid)
            continue
        rows.append((qid, topic, hit[0], hit[1], excerpt))
    if failed:
        print("NOT VERBATIM / NOT FOUND:", ", ".join(failed))
        sys.exit(1)
    out = os.path.join(EVIDENCE, "maintainer-quotes.md")
    with open(out, "w") as w:
        w.write("# Maintainer quotes register\n\n")
        w.write("Generated by `scripts/build_quotes.py`. Every excerpt is verified verbatim against\n")
        w.write("`maintainer-messages.txt` (typos kept; `...` is the maintainer's own punctuation, never an elision).\n")
        w.write("Timestamps are UTC. Cite as `[P1]`, `[M3]`, ... in the skeletons.\n\n")
        w.write("Prefixes: P process, A AI use, K KlangScript, S Sprudel, M Motor, D DSLs, SD sound, PF performance, F federation.\n\n")
        for qid, topic, ts, sid, excerpt in rows:
            quoted = "\n".join("> " + line if line else ">" for line in excerpt.split("\n"))
            w.write("### [%s] %s | %s | session `%s`\n\n%s\n\n" % (qid, topic, ts, sid, quoted))
    print("quotes verified: %d" % len(rows))


if __name__ == "__main__":
    main()
