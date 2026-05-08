package com.juni.app.domain.tools

class ToolRegistry(tools: List<Tool>) {
    private val byName: Map<String, Tool> = tools.associateBy { it.spec.name }
    val specs: List<ToolSpec> = tools.map { it.spec }

    fun find(name: String): Tool? = byName[name]
}
