package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;

import javax.lang.model.element.VariableElement;
import javax.xml.crypto.Data;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

/**
 * Created: 19-01-10
 * Last Changed: 21-09-28
 * Author: Esther Guerrier
 * <p>
 * Description: Ce visiteur explorer l'AST est renvois des erreur lorqu'une erreur sémantique est détecté.
 */

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter writer;

    private HashMap<String, VarType> symbolTable = new HashMap<>(); // mapping variable -> type

    // variable pour les metrics
    private int VAR = 0;
    private int WHILE = 0;
    private int IF = 0;
    private int FOR = 0;
    private int OP = 0;

    public SemantiqueVisitor(PrintWriter writer) {
        this.writer = writer;
    }

    /*
    Le Visiteur doit lancer des erreurs lorsqu'un situation arrive.

    regardez l'énoncé ou les tests pour voir le message à afficher et dans quelle situation.
    Lorsque vous voulez afficher une erreur, utilisez la méthode print implémentée ci-dessous.
    Tous vos tests doivent passer!!

     */

    @Override
    public Object visit(SimpleNode node, Object data) {
        return null;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        DataStruct d = new DataStruct();
        node.childrenAccept(this, d);
        writer.print(String.format("{VAR:%d, WHILE:%d, IF:%d, FOR:%d, OP:%d}", VAR, WHILE, IF, FOR, OP));

        return null;
    }

    /*
    Ici se retrouve les noeuds servant à déclarer une variable.
    Certaines doivent enregistrer les variables avec leur type dans la table symbolique.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTNormalDeclaration node, Object data) {
        VAR++;
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if(symbolTable.containsKey(varName))
            throw new SemantiqueError(String.format("Invalid declaration... variable %s already exists", varName));

        VarType type = node.getValue().equals("num") ? VarType.num : VarType.bool;
        symbolTable.put(varName, type);
        ((DataStruct)data).type = type;
        return null;
    }

    @Override
    public Object visit(ASTListDeclaration node, Object data) {
        VAR++;
        String varName = ((ASTIdentifier) node.jjtGetChild(0)).getValue();

        if(symbolTable.containsKey(varName))
            throw new SemantiqueError(String.format("Invalid declaration... variable %s already exists", varName));

        symbolTable.put(varName, node.getValue().equals("listnum") ? VarType.listnum : VarType.listbool);
        return null;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }


    @Override
    public Object visit(ASTStmt node, Object data) {

        node.childrenAccept(this, data);
        return null;
    }

    /*
     * Il faut vérifier que le type déclaré à gauche soit compatible avec la liste utilisée à droite. N'oubliez pas
     * de vérifier que les variables existent.
     */

    @Override
    public Object visit(ASTForEachStmt node, Object data) {
        FOR++;

        node.jjtGetChild(0).jjtAccept(this,data);
        VarType leftType = ((DataStruct)data).type;

        node.jjtGetChild(1).jjtAccept(this,data);
        String arrayName = ((ASTIdentifier) node.jjtGetChild(1)).getValue();

        VarType rightType = symbolTable.get(arrayName);
        if(!rightType.equals(VarType.listbool) && !rightType.equals(VarType.listnum))
            throw new SemantiqueError("Array type is required here...");
        if(!rightType.equals(leftType.equals(VarType.num) ? VarType.listnum : VarType.listbool))
            throw new SemantiqueError(String.format("Array type %s is incompatible with declared variable of type %s...", rightType, leftType));

        node.jjtGetChild(2).jjtAccept(this,data);
        return null;
    }

    /*
    Ici faites attention!! Lisez la grammaire, c'est votre meilleur ami :)
     */
    @Override
    public Object visit(ASTForStmt node, Object data) {
        FOR++;
        callChildrenCond(node, data, 1);
        return null;
    }

    /*
    Méthode recommandée à implémenter puisque vous remarquerez que quelques fonctions ont exactement le même code! N'oubliez
    -pas que la qualité du code est évalué :)
     */
    private void callChildrenCond(SimpleNode node, Object data, int index) {
        for(int i = 0; i < node.jjtGetNumChildren(); i++){
            if(i == index)
                continue;
            node.jjtGetChild(i).jjtAccept(this,data);
        }
        node.jjtGetChild(index).jjtAccept(this, data);
        if(((DataStruct)data).type != VarType.bool)
            throw new SemantiqueError(String.format("Invalid type in condition"));
    }

    /*
    les structures conditionnelle doivent vérifier que leur expression de condition est de type booléenne
    On doit aussi compter les conditions dans les variables IF et WHILE
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        IF++;
        callChildrenCond(node,data, 0);
        return null;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        WHILE++;
        callChildrenCond(node,data, 0);
        return null;
    }

    /*
    On doit vérifier que le type de la variable est compatible avec celui de l'expression.
    La variable doit etre déclarée.
     */
    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        node.childrenAccept(this, data);
        String varName = ((ASTIdentifier)node.jjtGetChild(0)).getValue();


        VarType type = this.symbolTable.get(varName);
        if(!type.equals(((DataStruct)data).type))
            throw new SemantiqueError(String.format("Invalid type in assignation of Identifier %s... was expecting %s but got %s",varName,type, ((DataStruct)data).type));

        return null;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        //Il est normal que tous les noeuds jusqu'à expr retourne un type.
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        /*attention, ce noeud est plus complexe que les autres.
        si il n'a qu'un seul enfant, le noeud a pour type le type de son enfant.

        si il a plus d'un enfant, alors ils s'agit d'une comparaison. il a donc pour type "Bool".

        de plus, il n'est pas acceptable de faire des comparaisons de booleen avec les opérateur < > <= >=.
        les opérateurs == et != peuvent être utilisé pour les nombres et les booléens, mais il faut que le type soit le même
        des deux côté de l'égalité/l'inégalité.
        */

        ArrayList<VarType> childrenTypes = new ArrayList<>();
        for(int i = 0; i < node.jjtGetNumChildren(); i++){
            node.jjtGetChild(i).jjtAccept(this,data);
            childrenTypes.add(((DataStruct)data).type);
        }
        if(childrenTypes.size() == 1)
            return null;

        if(!childrenTypes.get(0).equals(childrenTypes.get(1)))
            throw new SemantiqueError("Invalid type in expression");

        String operator = node.getValue();
        if(!operator.equals("==") && !operator.equals("!=") && !childrenTypes.get(0).equals(VarType.num))
            throw new SemantiqueError("Invalid type in expression");

        ((DataStruct)data).type = VarType.bool;
        OP++;
        return null;
    }

    private void callChildren(SimpleNode node, Object data, VarType validType) {
        int numChildren = node.jjtGetNumChildren();
        if(numChildren == 1){
            node.childrenAccept(this,data);
            return;
        }

        for(int i = 0; i < numChildren; i++){
            OP++;
            node.jjtGetChild(i).jjtAccept(this,data);
            if(!(((DataStruct)data).type).equals(validType))
                throw new SemantiqueError("Invalid type in expression");
        }
        OP--;
    }

    /*
    opérateur binaire
    si il n'y a qu'un enfant, aucune vérification à faire.
    par exemple, un AddExpr peut retourné le type "Bool" à condition de n'avoir qu'un seul enfant.
     */
    @Override
    public Object visit(ASTAddExpr node, Object data) {
        callChildren(node,data,VarType.num);

        return null;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        callChildren(node,data,VarType.num);

        return null;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        callChildren(node,data,VarType.bool);

        return null;
    }

    /*
    opérateur unaire
    les opérateur unaire ont toujours un seul enfant.

    Cependant, ASTNotExpr et ASTUnaExpr ont la fonction "getOps()" qui retourne un vecteur contenant l'image (représentation str)
    de chaque token associé au noeud.

    Il est utile de vérifier la longueur de ce vecteur pour savoir si une opérande est présente.

    si il n'y a pas d'opérande, ne rien faire.
    si il y a une (ou plus) opérande, ils faut vérifier le type.

    */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        Vector ops = node.getOps();
        node.childrenAccept(this, data);
        if(ops.size() > 0 && ((DataStruct)data).type.equals(VarType.num))
            throw new SemantiqueError("Invalid type in expression");
        OP += ops.size();

        return null;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        Vector ops = node.getOps();
        node.childrenAccept(this, data);
        if(ops.size() > 0 && ((DataStruct)data).type.equals(VarType.bool))
            throw new SemantiqueError("Invalid type in expression");

        OP += ops.size();

        return null;
    }

    /*
    les noeud ASTIdentifier aillant comme parent "GenValue" doivent vérifier leur type et vérifier leur existence.

    Ont peut envoyé une information a un enfant avec le 2e paramètre de jjtAccept ou childrenAccept.
     */
    @Override
    public Object visit(ASTGenValue node, Object data) {
        node.childrenAccept(this, data);
        return null;
    }


    @Override
    public Object visit(ASTBoolValue node, Object data) {
        ((DataStruct)data).type = VarType.bool;
        node.childrenAccept(this, data);
        return null;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        String varName = node.getValue();
        if(!(node.jjtGetParent() instanceof ASTNormalDeclaration) && !symbolTable.containsKey(varName)) {
            throw new SemantiqueError(String.format("Invalid use of undefined Identifier %s", varName));
        }
        if(node.jjtGetParent() instanceof ASTGenValue){
            VarType type = symbolTable.get(varName);
            ((DataStruct)data).type = type;
        }
        return null;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        ((DataStruct)data).type = VarType.num;
        node.childrenAccept(this, data);
        return null;
    }


    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        bool,
        num,
        listnum,
        listbool
    }

    private class DataStruct {
        public VarType type;

        public DataStruct() {
        }

        public DataStruct(VarType p_type) {
            type = p_type;
        }
    }
}
