package analyzer.ast;/* Generated By:JJTree: Do not edit this line. ASTIOStmt.java */

public class ASTIOStmt extends SimpleNode {
  public ASTIOStmt(int id) {
    super(id);
  }

  public ASTIOStmt(Parser p, int id) {
    super(p, id);
  }


  /** Accept the visitor. **/
  public Object jjtAccept(ParserVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  // PLB
  private String m_op = null;
  public void setOp(String o) { m_op = o; }
  public String getOp() { return m_op; }
}