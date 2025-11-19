package io.github.kyay10.prettifykotlin

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childLeafs
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import java.util.regex.PatternSyntaxException

class TokenFoldingBuilder : FoldingBuilderEx(), DumbAware {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean) = with(document) {
    buildList {
      val state = root.project.service<Settings>()
      if (!state.isEnabled) return@buildList
      root.childLeafs().forEach { element ->
        state.tokenToReplacement[(element.elementType as? KtSingleValueToken)?.value]?.let { replacement ->
          add(prettyFoldingDescriptor(element, replacement))
          return@forEach
        }
        if (element.elementType == KtTokens.IDENTIFIER) {
          val replacement =
            state.identifierToReplacement.fold(element.text) { acc, (regex, replacement) ->
              try {
                acc.replace(regex.toRegex(), replacement)
              } catch (_: PatternSyntaxException) {
                acc
              }
            }
          add(prettyFoldingDescriptor(element, replacement))
        }
      }
    }.filterNotNull().toTypedArray()
  }

  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}
