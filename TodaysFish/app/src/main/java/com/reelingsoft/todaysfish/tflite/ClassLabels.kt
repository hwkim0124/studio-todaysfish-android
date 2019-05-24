package com.reelingsoft.todaysfish.tflite


object ClassLabels {

    private const val NUM_CLASSES = 36
    lateinit var classes: Array<String>

    init {
        classes = arrayOf(
            "배경",
            "감성돔",
            "군평선이",
            "금눈돔",
            "구갈돔",
            "능성어",
            "쥐노래미",
            "자리돔",
            "돌돔",
            "동갈돔",
            "범돔",
            "벵에돔",
            "망상어",
            "긴꼬리벵에돔",
            "벤자리",
            "붉바리",
            "붉돔",
            "샛돔",
            "옥돔",
            "어름돔",
            "자바리",
            "참돔",
            "황돔",
            "청돔",
            "혹돔",
            "다금바리",
            "강담돔",
            "인상어",
            "병어",
            "전갱이",
            "노래미",
            "쏨뱅이",
            "붉은쏨뱅이",
            "쭈굴감펭",
            "갈볼락",
            "불볼락"
        )

        assert(classes.size == NUM_CLASSES)
    }

    fun getClassName(id: Int): String {
        return classes[id]
    }
}