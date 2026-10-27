package app.vultrpocket.mobile;

public final class PasswordPolicyTest {
    public static void main(String[] args) {
        if(!PasswordPolicy.needsSetup("",""))throw new AssertionError("fresh install");
        if(PasswordPolicy.needsSetup("configured-hash","configured-salt"))throw new AssertionError("configured password overwritten");
        if(PasswordPolicy.needsSetup("configured-hash",""))throw new AssertionError("partial credential reset");
        if(PasswordPolicy.needsSetup("","configured-salt"))throw new AssertionError("partial credential reset");
        if(PasswordPolicy.needsSetup(null,null))throw new AssertionError("null state reset");
        if(!PasswordPolicy.acceptable("eight-ok","eight-ok"))throw new AssertionError("eight character password rejected");
        if(PasswordPolicy.acceptable("short","short"))throw new AssertionError("short password accepted");
        if(PasswordPolicy.acceptable("eight-ok","different"))throw new AssertionError("confirmation mismatch");
        if(PasswordPolicy.acceptable(new String(new char[513]),new String(new char[513])))throw new AssertionError("oversize password accepted");
        System.out.println("PasswordPolicy: 9/9 assertions passed");
    }
}
