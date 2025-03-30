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

private val identifierToReplacement = listOf(
  "^shl$".toRegex() to "<<",
  "^shr$".toRegex() to ">>",
  "^ushr$".toRegex() to ">>>",
  "^and$".toRegex() to "&",
  "^or$".toRegex() to "|",
  "^xor$".toRegex() to "^",
  "^`([^`]+)`$".toRegex() to "$1",
)

class TokenFoldingBuilder : FoldingBuilderEx(), DumbAware {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    buildList {
      root.childLeafs().forEach { element ->
        tokenToReplacement[element.elementType]?.let { replacement ->
          add(prettyFoldingDescriptor(element, replacement))
          return@forEach
        }
        if (element.elementType == KtTokens.IDENTIFIER) {
          identifierToReplacement.fold(element.text) { acc, (regex, replacement) ->
            acc.replace(regex, replacement)
          }.takeIf { it != element.text }?.let { replacement ->
            add(prettyFoldingDescriptor(element, replacement))
          }
        }
      }
    }.toTypedArray()

  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}
