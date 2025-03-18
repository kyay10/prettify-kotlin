package io.github.kyay10.prettifykotlin

import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.psi.PsiElement

class PrettyFoldingDescriptor(
  node: PsiElement, placeholder: String, group: FoldingGroup? = null
) : FoldingDescriptor(node.node, node.textRange, group, placeholder) {
   override fun isNonExpandable(): Boolean = true
}