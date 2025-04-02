package io.github.kyay10.prettifykotlin

import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.name.Name

fun prettyFoldingDescriptor(
  node: PsiElement,
  placeholder: String,
  range: TextRange = node.textRange,
  dependencies : Set<Any?> = setOf(),
) = FoldingDescriptor(node.node, range, null, dependencies, true, placeholder, true).apply {
  setCanBeRemovedWhenCollapsed(true)
}.takeIf { range.shiftLeft(node.textRange.startOffset).substring(node.text) != placeholder }

fun KaAnnotation.findConstantArgument(name: Name): Any? = findConstantArgument(name) { null }

inline fun <reified T> KaAnnotation.findConstantArgument(name: Name, default: () -> T): T {
  val constValue = (arguments.find { it.name == name }?.expression as? KaAnnotationValue.ConstantValue)?.value
  val value = constValue?.value
  if (constValue == null || value !is T) return default()
  return value
}

val PsiElement.foldForSingleSpaceBefore: FoldingDescriptor?
  get() = prevSibling.takeIf { it.text.endsWith(" ") }?.let {
    prettyFoldingDescriptor(it, "", range = TextRange(it.textRange.endOffset - 1, it.textRange.endOffset))
  }

val PsiElement.foldForSingleSpaceAfter: FoldingDescriptor?
  get() = nextSibling.takeIf { it.text.startsWith(" ") }?.let {
    prettyFoldingDescriptor(it, "", range = TextRange(it.textRange.startOffset, it.textRange.startOffset + 1))
  }