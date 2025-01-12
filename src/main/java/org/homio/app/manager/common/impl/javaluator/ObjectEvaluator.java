package org.homio.app.manager.common.impl.javaluator;

import com.fathzer.soft.javaluator.*;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fathzer.soft.javaluator.DoubleEvaluator.*;

public class ObjectEvaluator extends AbstractEvaluator<Object> {

  private static final Pattern SCIENTIFIC_NOTATION_PATTERN = Pattern.compile("([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)[eE][+-]?\\d+)$");
  private static final ThreadLocal<NumberFormat> FORMATTER = ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.US));

  private boolean supportsScientificNotation;

  /**
   * Constructor.
   * <br>This default constructor builds an instance with all predefined operators, functions and constants.
   */
  public ObjectEvaluator(Parameters params) {
    super(params);
  }

  public static boolean isScientificNotation(String str) {
    Matcher matcher = SCIENTIFIC_NOTATION_PATTERN.matcher(str);
    if (!matcher.find()) {
      return false;
    }
    String matched = matcher.group();
    return matched.length() == str.length();
  }

  @Override
  protected Iterator<String> tokenize(String expression) {
    if (supportsScientificNotation) {
      // There's a trap with scientific number notation (1E+50 for example):
      // + is considered as an operator. We'll make a basic work around...
      final List<String> tokens = new ArrayList<>();
      final Iterator<String> rawTokens = super.tokenize(expression);
      while (rawTokens.hasNext()) {
        tokens.add(rawTokens.next());
      }
      for (int i = 1; i < tokens.size() - 1; i++) {
        testScientificNotation(tokens, i);
      }
      return tokens.iterator();
    } else {
      return super.tokenize(expression);
    }
  }

  private void testScientificNotation(List<String> tokens, int index) {
    final String previous = tokens.get(index - 1);
    final String next = tokens.get(index + 1);
    final String current = tokens.get(index);
    final String candidate = previous + current + next;
    if (isScientificNotation(candidate)) {
      tokens.set(index - 1, candidate);
      tokens.remove(index);
      tokens.remove(index);
    }
  }

  @Override
  protected Double toValue(String literal, Object evaluationContext) {
    ParsePosition p = new ParsePosition(0);
    Number result = FORMATTER.get().parse(literal, p);
    if (p.getIndex() == 0 || p.getIndex() != literal.length()) {
      // For an unknown reason, NumberFormat.getNumberInstance(...) returns a formatter that does not tolerate
      // scientific notation :-(
      // Let's try with Double.parse(...)
      if (supportsScientificNotation && isScientificNotation(literal)) {
        return Double.valueOf(literal);
      }
      throw new IllegalArgumentException(literal + " is not a number");
    }
    return result.doubleValue();
  }

  /* (non-Javadoc)
   * @see net.astesana.javaluator.AbstractEvaluator#evaluate(net.astesana.javaluator.Constant)
   */
  @Override
  protected Object evaluate(Constant constant, Object evaluationContext) {
    if (PI.equals(constant)) {
      return Math.PI;
    } else if (E.equals(constant)) {
      return Math.E;
    } else {
      return super.evaluate(constant, evaluationContext);
    }
  }

  /* (non-Javadoc)
   * @see net.astesana.javaluator.AbstractEvaluator#evaluate(net.astesana.javaluator.Operator, java.util.Iterator)
   */
  @Override
  protected Object evaluate(Operator operator, Iterator<Object> operands, Object evaluationContext) {
    if (NEGATE.equals(operator) || NEGATE_HIGH.equals(operator)) {
      return -(double) operands.next();
    } else if (MINUS.equals(operator)) {
      return (double) operands.next() - (double) operands.next();
    } else if (PLUS.equals(operator)) {
      return (double) operands.next() + (double) operands.next();
    } else if (MULTIPLY.equals(operator)) {
      return (double) operands.next() * (double) operands.next();
    } else if (DIVIDE.equals(operator)) {
      return (double) operands.next() / (double) operands.next();
    } else if (EXPONENT.equals(operator)) {
      return Math.pow((double) operands.next(), (double) operands.next());
    } else if (MODULO.equals(operator)) {
      return (double) operands.next() % (double) operands.next();
    } else {
      return super.evaluate(operator, operands, evaluationContext);
    }
  }

  /* (non-Javadoc)
   * @see net.astesana.javaluator.AbstractEvaluator#evaluate(net.astesana.javaluator.Function, java.util.Iterator)
   */
  @Override
  protected Object evaluate(Function function, Iterator<Object> arguments, Object evaluationContext) {
    Object result;
    if (ABS.equals(function)) {
      result = Math.abs((double) arguments.next());
    } else if (CEIL.equals(function)) {
      result = Math.ceil((double) arguments.next());
    } else if (FLOOR.equals(function)) {
      result = Math.floor((double) arguments.next());
    } else if (ROUND.equals(function)) {
      Double arg = (double) arguments.next();
      if (arg == Double.NEGATIVE_INFINITY || arg == Double.POSITIVE_INFINITY) {
        result = arg;
      } else {
        result = (double) Math.round(arg);
      }
    } else if (SINEH.equals(function)) {
      result = Math.sinh((double) arguments.next());
    } else if (COSINEH.equals(function)) {
      result = Math.cosh((double) arguments.next());
    } else if (TANGENTH.equals(function)) {
      result = Math.tanh((double) arguments.next());
    } else if (SINE.equals(function)) {
      result = Math.sin((double) arguments.next());
    } else if (COSINE.equals(function)) {
      result = Math.cos((double) arguments.next());
    } else if (TANGENT.equals(function)) {
      result = Math.tan((double) arguments.next());
    } else if (ACOSINE.equals(function)) {
      result = Math.acos((double) arguments.next());
    } else if (ASINE.equals(function)) {
      result = Math.asin((double) arguments.next());
    } else if (ATAN.equals(function)) {
      result = Math.atan((double) arguments.next());
    } else if (MIN.equals(function)) {
      result = arguments.next();
      while (arguments.hasNext()) {
        result = Math.min((double) result, (double) arguments.next());
      }
    } else if (MAX.equals(function)) {
      result = arguments.next();
      while (arguments.hasNext()) {
        result = Math.max((double) result, (double) arguments.next());
      }
    } else if (SUM.equals(function)) {
      result = 0.;
      while (arguments.hasNext()) {
        result = (double) result + (double) arguments.next();
      }
    } else if (AVERAGE.equals(function)) {
      result = 0.;
      int nb = 0;
      while (arguments.hasNext()) {
        result = (double) result + (double) arguments.next();
        nb++;
      }
      // Remember that method is called only if the number of parameters match with the function
      // definition => nb will never remain 0 (There's a junit test that fails if it would not be the case).
      result = (double) result / nb;
    } else if (LN.equals(function)) {
      result = Math.log((double) arguments.next());
    } else if (LOG.equals(function)) {
      result = Math.log10((double) arguments.next());
    } else if (RANDOM.equals(function)) {
      result = Math.random();
    } else {
      result = super.evaluate(function, arguments, evaluationContext);
    }
    errIfNaN((double) result, function);
    return result;
  }

  private void errIfNaN(Double result, Function function) {
    if (result.equals(Double.NaN)) {
      throw new IllegalArgumentException("Invalid argument passed to " + function.getName());
    }
  }
}
