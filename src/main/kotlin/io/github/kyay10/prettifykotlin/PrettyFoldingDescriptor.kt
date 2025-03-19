package io.github.kyay10.prettifykotlin

import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class PrettyFoldingDescriptor(
  node: PsiElement, placeholder: String, range: TextRange = node.textRange, group: FoldingGroup? = null, neverExpands: Boolean = true
) : FoldingDescriptor(node.node, range, group, mutableSetOf<Any?>(), neverExpands, placeholder, true)