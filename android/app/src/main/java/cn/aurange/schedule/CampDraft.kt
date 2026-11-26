package cn.aurange.schedule

import java.time.LocalDate

data class CampDraft(val term: Int, val openDate: LocalDate, val skips: Set<Int> = emptySet(), val examWeek: Int = 6) {
    fun error(camps: List<Camp>, original: Camp? = null): String? = when {
        term !in 1..10000 -> "期数请输入 1～10000 之间的整数。"
        camps.any { it.term == term && it.id != original?.id } -> "第 $term 期已存在，请换一个期数。"
        openDate.year !in 2000..2100 -> "开营日期请选择 2000～2100 年之间。"
        skips.any { it !in 1..12 } -> "休息周应在第 1～12 周之间。"
        examWeek !in 1..12 -> "考试周请选择第 1～12 周。"
        else -> null
    }
}
