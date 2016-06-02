package jemu.core.breakpoint;

import jemu.core.device.*;

/**
 * A single Breakpoint. Can represent an Assembler, Stepping or User breakpoint.
 * Breakpoints can also have compiled conditions. Yet to be completed.
 *
 * @author Richard Wilson
 *
 * @version 1.0
 */
public class Breakpoint {
  
  // Breakpoint types must be powers of 2 (ie. 1, 2, 4 etc).
  
  // User breakpoint
  public static final int USER = 1;
  
  // Step Over breakpoint
  public static final int STEP_OVER = 2;
  
  // Assembler breakpoint
  public static final int ASSEMBLER = 3;
  
  // The Computer on which this Breakpoint is set
  public Computer computer;
  
  // The breakpoint address
  public int address;
  
  // The type of breakpoint
  public int type;
  
  // The breakpoint condition
  public String condition = null;
  
  // The compiled condition
  public int[] tokens;
  
  // Condition tokens
  public static final int CONSTANT = 0;
  public static final int ADD      = 1;
  public static final int SUBTRACT = 2;
  public static final int MULTIPLY = 3;
  public static final int DIVIDE   = 4;
  public static final int AND      = 5;
  public static final int OR       = 6;
  public static final int XOR      = 7;
  
  public Breakpoint(Computer computer, int address, int type) {
    this.computer = computer;
    this.address = address;
    this.type = type;
  }
  
  public void setCondition(String value) throws Exception {
    if (condition != value) {
      condition = value == null ? null : value.trim();
    
      // Compile it
      if (value == null || value.length() == 0)
        tokens = null;
      else {
        
      }
    }
  }
  
  public boolean testCondition() {
    if (tokens == null) return true;
    int[] stack = new int[256];
    int sp = -1;
    for (int i = 0; i < tokens.length; i++) {
      switch(tokens[i]) {
        case CONSTANT: stack[++sp] = tokens[++i]; break;
        case ADD:      sp--; stack[sp] = stack[sp] + stack[sp + 1]; break;
        case SUBTRACT: sp--; stack[sp] = stack[sp] - stack[sp + 1]; break;
        case MULTIPLY: sp--; stack[sp] = stack[sp] * stack[sp + 1]; break;
        case DIVIDE:   sp--; stack[sp] = stack[sp] / stack[sp + 1]; break;
        case AND:      sp--; stack[sp] = stack[sp] & stack[sp + 1]; break;
        case OR:       sp--; stack[sp] = stack[sp] | stack[sp + 1]; break;
        case XOR:      sp--; stack[sp] = stack[sp] ^ stack[sp + 1]; break;
      }
    }
    return stack[0] != 0;
  }

}
