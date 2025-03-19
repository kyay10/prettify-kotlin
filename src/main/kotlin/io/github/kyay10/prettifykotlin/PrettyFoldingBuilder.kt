package io.github.kyay10.prettifykotlin

import arrow.core.raise.impure
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.findAnnotation
import org.jetbrains.kotlin.idea.util.isMultiline
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.ValueArgument
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.toUElementOfType
import org.toml.lang.psi.ext.elementType

val PRETTY_FQNAME = FqName("io.github.kyay10.prettifykotlin.Pretty")
val PREFIX_FQNAME = FqName("io.github.kyay10.prettifykotlin.Prefix")
val POSTFIX_FQNAME = FqName("io.github.kyay10.prettifykotlin.Postfix")
val AUTOLAMBDA_FQNAME = FqName("io.github.kyay10.prettifykotlin.AutoLambda")

class PrettyFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    if (quick) emptyArray()
    else buildList {
      root.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) = impure {
          super.visitSimpleNameExpression(expression)

          ensure(expression.parent !is KtThisExpression && expression.parent !is KtSuperExpression && expression !is KtLabelReferenceExpression)
          val reference = expression.mainReference.resolve()
          ensure(reference is KtAnnotated)

          val annotation = reference.findAnnotation(PRETTY_FQNAME).bind()
          val name = annotation.valueArguments.singleOrNull().bind().constValue
          ensure(name is String)

          add(PrettyFoldingDescriptor(expression, name))
        }

        override fun visitCallExpression(expression: KtCallExpression) = impure {
          super.visitCallExpression(expression)
          val reference = expression.calleeExpression.bind().mainReference.bind().resolve()
          ensure(reference is KtAnnotated)
          impure {
            val annotation = reference.findAnnotation(PREFIX_FQNAME).bind()
            val (prefix, suffix) = annotation.valueArguments.also { ensure(it.size == 2) }.map { it.constValue }
            ensure(prefix is String && suffix is String)

            val (leftPar, rightPar) = expression.valueArgumentList.bind().run {
              leftParenthesis.bind() to rightParenthesis.bind()
            }
            add(PrettyFoldingDescriptor(leftPar, prefix))
            add(PrettyFoldingDescriptor(rightPar, suffix))
          }
          impure {
            ensure(reference is KtFunction)
            val params = reference.valueParameters
            val argToParam = expression.valueArguments.mapIndexed { index, arg ->
              arg to (arg.name?.let { name -> params.find { it.name == name }.bind() } ?: params.getOrNull(index)
                .bind())
            }
            argToParam.forEach { (arg, param) ->
              val annotation = param.findAnnotation(AUTOLAMBDA_FQNAME) ?: return@forEach
              val (prefix, arrow, suffix) = annotation.valueArguments.also { ensure(it.size == 3) }
                .map { it.constValue }
              ensure(prefix is String && arrow is String && suffix is String)
              val lambda = (arg.getArgumentExpression() as? KtLambdaExpression ?: return@forEach).bind().functionLiteral
              add(PrettyFoldingDescriptor(lambda.lBrace, prefix))
              val lBraceWhitespace = lambda.lBrace.nextSibling
              if (lBraceWhitespace.text.startsWith(" "))
                add(PrettyFoldingDescriptor(lBraceWhitespace, "", range = TextRange(lBraceWhitespace.textRange.startOffset, lBraceWhitespace.textRange.startOffset + 1)))
              lambda.arrow?.let {
                add(PrettyFoldingDescriptor(it, arrow))
                val afterArrowWhitespace = it.nextSibling
                if (afterArrowWhitespace.text.startsWith(" "))
                  add(PrettyFoldingDescriptor(afterArrowWhitespace, "", range = TextRange(afterArrowWhitespace.textRange.startOffset, afterArrowWhitespace.textRange.startOffset + 1)))
                val beforeArrowWhitespace = it.prevSibling
                if (beforeArrowWhitespace.text.endsWith(" "))
                  add(PrettyFoldingDescriptor(beforeArrowWhitespace, "", range = TextRange(beforeArrowWhitespace.textRange.endOffset - 1, beforeArrowWhitespace.textRange.endOffset)))
              }
              lambda.rBrace?.let {
                add(PrettyFoldingDescriptor(it, suffix))
                if(!lambda.isMultiline()) {
                  val rBraceWhitespace = it.prevSibling
                  if (rBraceWhitespace.text.endsWith(" "))
                    add(PrettyFoldingDescriptor(rBraceWhitespace, "", range = TextRange(rBraceWhitespace.textRange.endOffset - 1, rBraceWhitespace.textRange.endOffset)))
                }
              }
            }
          }
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) = impure {
          super.visitDotQualifiedExpression(expression)
          val selector = expression.selectorExpression
          ensure(selector is KtSimpleNameExpression)
          val reference = selector.mainReference.bind().resolve()
          ensure(reference is KtAnnotated)

          val annotation = reference.findAnnotation(POSTFIX_FQNAME).bind()
          val suffix = annotation.valueArguments.singleOrNull().bind().constValue
          ensure(suffix is String)
          add(PrettyFoldingDescriptor(expression.allChildren.first { it.elementType == KtTokens.DOT }, suffix))
        }
      })
    }.toTypedArray()


  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

private val ValueArgument.constValue: Any?
  get() = getArgumentExpression()?.toUElementOfType<UExpression>()?.evaluate()