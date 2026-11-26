package cn.aurange.schedule

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ConfirmedSnapshotTest {
    @Test fun lateResponseCannotUndoAnotherConfirmedTaskOrNewerVersion() {
        val current = Snapshot(tasks = mapOf("a" to TaskState(false, 3), "b" to TaskState(true, 1)), syncedAt = 20)
        val earlier = Snapshot(tasks = mapOf("a" to TaskState(true, 1)), syncedAt = 10)
        assertEquals(current, mergeConfirmedSnapshots(current, earlier))
        val next = mergeConfirmedSnapshots(current, Snapshot(tasks = mapOf("c" to TaskState(true, 1)), syncedAt = 10))
        assertEquals(current.tasks + ("c" to TaskState(true, 1)), next.tasks)
        assertEquals(20L, next.syncedAt)
    }

    @Test fun freshRefreshKeepsItsCampDatesWhileMergingAcknowledgedTaskVersions() {
        val original = Camp("camp", 1, LocalDate.parse("2026-10-25"))
        val changed = original.copy(openDate = original.openDate.plusWeeks(1), version = 2)
        val current = Snapshot(listOf(changed), mapOf("task" to TaskState(true, 2)), 30)
        val ack = Snapshot(listOf(original), mapOf("task" to TaskState(false, 1)), 20)
        assertEquals(current, mergeConfirmedSnapshots(current, ack))
        assertEquals(current, mergeConfirmedSnapshots(ack, current))
    }
}
