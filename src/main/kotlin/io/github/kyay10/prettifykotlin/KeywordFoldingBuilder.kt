package io.github.kyay10.prettifykotlin

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childLeafs
import org.jetbrains.kotlin.lexer.KtTokens
import org.toml.lang.psi.ext.elementType

private val tokenToReplacement = mapOf(
  KtTokens.IN_KEYWORD to "∈",
  KtTokens.NOT_IN to "∉",
  KtTokens.FOR_KEYWORD to "∀",
)
private val identifierToReplacement = mapOf(
  "shl" to "<<",
  "shr" to ">>",
  "ushr" to ">>>",
  "and" to "&",
  "or" to "|",
  "xor" to "^",
)

class KeywordFoldingBuilder : FoldingBuilderEx(), DumbAware {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    buildList {
      root.childLeafs().forEach { element ->
        tokenToReplacement[element.elementType]?.let { replacement ->
          add(PrettyFoldingDescriptor(element, replacement))
        } ?: if (element.elementType == KtTokens.IDENTIFIER) {
          identifierToReplacement[element.text]?.let { replacement ->
            add(PrettyFoldingDescriptor(element, replacement))
          }
        } else Unit
      }
    }.toTypedArray()

  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}
