package io.github.kyay10.prettifykotlin

import arrow.core.raise.impure
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotated
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotation
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.isMultiline
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLabelReferenceExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtSuperExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.toml.lang.psi.ext.elementType

val PACKAGE_FQNAME = FqName("io.github.kyay10.prettifykotlin")
val PRETTY = ClassId(PACKAGE_FQNAME, Name.identifier("Pretty"))
val PRETTY_NAME = Name.identifier("name")
val PREFIX = ClassId(PACKAGE_FQNAME, Name.identifier("Prefix"))
val PREFIX_PREFIX = Name.identifier("prefix")
val PREFIX_SUFFIX = Name.identifier("suffix")
val POSTFIX = ClassId(PACKAGE_FQNAME, Name.identifier("Postfix"))
val POSTFIX_SUFFIX = Name.identifier("suffix")
val AUTOLAMBDA = ClassId(PACKAGE_FQNAME, Name.identifier("AutoLambda"))
val AUTOLAMBDA_PREFIX = Name.identifier("prefix")
val AUTOLAMBDA_ARROW = Name.identifier("arrow")
val AUTOLAMBDA_SUFFIX = Name.identifier("suffix")

class PrettyFoldingBuilder : FoldingBuilderEx() {
  override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> =
    if (quick) emptyArray()
    else buildList {
      root.accept(object : KtTreeVisitorVoid() {
        override fun visitSimpleNameExpression(expression: KtSimpleNameExpression) = impure {
          super.visitSimpleNameExpression(expression)

          ensure(expression.parent !is KtThisExpression && expression.parent !is KtSuperExpression && expression !is KtLabelReferenceExpression)
          analyze(expression) {
            val reference = expression.mainReference.resolveToSymbol()
            ensure(reference is KaAnnotated)
            val annotation = reference.annotations[PRETTY].singleOrNull().bind()
            val name = annotation.findConstantArgument(PRETTY_NAME)
            ensure(name is String)

            add(PrettyFoldingDescriptor(expression, name))
          }
        }

        override fun visitCallExpression(expression: KtCallExpression) = impure {
          super.visitCallExpression(expression)
          analyze(expression) {
            val reference = expression.calleeExpression.bind().mainReference.bind().resolveToSymbol()
            ensure(reference is KaAnnotated)
            impure {
              val annotation = reference.annotations[PREFIX].singleOrNull().bind()
              val prefix = annotation.findConstantArgument(PREFIX_PREFIX)
              val suffix = annotation.findConstantArgument(PREFIX_SUFFIX)
              ensure(prefix is String && suffix is String)

              val (leftPar, rightPar) = expression.valueArgumentList.bind().run {
                leftParenthesis.bind() to rightParenthesis.bind()
              }
              add(PrettyFoldingDescriptor(leftPar, prefix))
              add(PrettyFoldingDescriptor(rightPar, suffix))
            }
            impure {
              val call = expression.resolveToCall()?.successfulFunctionCallOrNull().bind()
              call.argumentMapping.forEach { (arg, param) ->
                val annotation = param.symbol.annotations[AUTOLAMBDA].singleOrNull() ?: return@forEach
                val prefix = annotation.findConstantArgument(AUTOLAMBDA_PREFIX)
                val arrow = annotation.findConstantArgument(AUTOLAMBDA_ARROW)
                val suffix = annotation.findConstantArgument(AUTOLAMBDA_SUFFIX)
                ensure(prefix is String && arrow is String && suffix is String)
                if (arg !is KtLambdaExpression) return@forEach
                val lambda = arg.functionLiteral
                add(PrettyFoldingDescriptor(lambda.lBrace, prefix))
                val lBraceWhitespace = lambda.lBrace.nextSibling
                if (lBraceWhitespace.text.startsWith(" "))
                  add(
                    PrettyFoldingDescriptor(
                      lBraceWhitespace,
                      "",
                      range = TextRange(
                        lBraceWhitespace.textRange.startOffset,
                        lBraceWhitespace.textRange.startOffset + 1
                      )
                    )
                  )
                lambda.arrow?.let {
                  add(PrettyFoldingDescriptor(it, arrow))
                  val afterArrowWhitespace = it.nextSibling
                  if (afterArrowWhitespace.text.startsWith(" "))
                    add(
                      PrettyFoldingDescriptor(
                        afterArrowWhitespace,
                        "",
                        range = TextRange(
                          afterArrowWhitespace.textRange.startOffset,
                          afterArrowWhitespace.textRange.startOffset + 1
                        )
                      )
                    )
                  val beforeArrowWhitespace = it.prevSibling
                  if (beforeArrowWhitespace.text.endsWith(" "))
                    add(
                      PrettyFoldingDescriptor(
                        beforeArrowWhitespace,
                        "",
                        range = TextRange(
                          beforeArrowWhitespace.textRange.endOffset - 1,
                          beforeArrowWhitespace.textRange.endOffset
                        )
                      )
                    )
                }
                lambda.rBrace?.let {
                  add(PrettyFoldingDescriptor(it, suffix))
                  if (!lambda.isMultiline()) {
                    val rBraceWhitespace = it.prevSibling
                    if (rBraceWhitespace.text.endsWith(" "))
                      add(
                        PrettyFoldingDescriptor(
                          rBraceWhitespace,
                          "",
                          range = TextRange(
                            rBraceWhitespace.textRange.endOffset - 1,
                            rBraceWhitespace.textRange.endOffset
                          )
                        )
                      )
                  }
                }
              }
            }
          }
        }

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) = impure {
          super.visitDotQualifiedExpression(expression)
          analyze(expression) {
            val selector = expression.selectorExpression
            ensure(selector is KtSimpleNameExpression)
            val reference = selector.mainReference.bind().resolveToSymbol()
            ensure(reference is KaAnnotated)
            val annotation = reference.annotations[POSTFIX].singleOrNull().bind()
            val suffix = annotation.findConstantArgument(POSTFIX_SUFFIX)
            ensure(suffix is String)
            add(PrettyFoldingDescriptor(expression.allChildren.first { it.elementType == KtTokens.DOT }, suffix))
          }
        }

      })
    }.toTypedArray()


  override fun getPlaceholderText(node: ASTNode) = null

  override fun isCollapsedByDefault(node: ASTNode): Boolean = true
}

private fun KaAnnotation.findConstantArgument(name: Name): Any? =
  (arguments.find { it.name == name }?.expression as? KaAnnotationValue.ConstantValue)?.value?.value