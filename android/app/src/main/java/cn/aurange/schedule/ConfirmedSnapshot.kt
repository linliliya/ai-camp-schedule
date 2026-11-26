package cn.aurange.schedule

// Responses can resume on the UI thread out of order. Only newer task versions win.
fun mergeConfirmedSnapshots(current: Snapshot, incoming: Snapshot): Snapshot {
    val base = if (incoming.syncedAt > current.syncedAt) incoming else current
    val tasks = current.tasks.toMutableMap()
    incoming.tasks.forEach { (id, state) ->
        if (state.version >= (tasks[id]?.version ?: -1)) tasks[id] = state
    }
    return base.copy(tasks = tasks)
}
