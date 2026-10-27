package app.vultrpocket.mobile;

public final class AddressPolicyTest {
    private static int passed;
    private static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;}
    public static void main(String[] args){
        check(AddressPolicy.copyable("192.0.2.1"),"IPv4");
        check(AddressPolicy.copyable("2001:db8::1"),"compressed IPv6");
        check(AddressPolicy.copyable("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff"),"full IPv6");
        check(AddressPolicy.copyable("::ffff:192.0.2.1"),"mapped IPv6");
        check(!AddressPolicy.copyable(null),"null");
        check(!AddressPolicy.copyable("example.com"),"hostname rejected");
        check(!AddressPolicy.copyable("256.0.2.1"),"IPv4 octet");
        check(!AddressPolicy.copyable("192.1"),"short IPv4");
        check(!AddressPolicy.copyable("ab:cd"),"invalid IPv6");
        check(!AddressPolicy.copyable("192.0.2.1\r\nsecret"),"newline rejected");
        check(!AddressPolicy.copyable(" 192.0.2.1"),"whitespace rejected");
        check(!AddressPolicy.copyable("https://192.0.2.1"),"URL rejected");
        System.out.println("AddressPolicy: "+passed+"/"+passed+" assertions passed");
    }
}
