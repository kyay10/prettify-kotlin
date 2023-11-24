package io.github.kyay10.prettifykotlin

import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.psi.PsiElement

class PrettyFoldingDescriptor(
  node: PsiElement, placeholder: String
) : FoldingDescriptor(node.node, node.textRange, null, placeholder) {
  override fun isNonExpandable(): Boolean = true
}