package io.github.kyay10.prettifykotlin

import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

fun prettyFoldingDescriptor(
  node: PsiElement,
  placeholder: String,
  range: TextRange = node.textRange,
  dependencies : Set<Any?> = setOf(),
) = FoldingDescriptor(node.node, range, null, dependencies, true, placeholder, true).apply {
  setCanBeRemovedWhenCollapsed(true)
}