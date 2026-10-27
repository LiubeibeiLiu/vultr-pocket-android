package app.vultrpocket.mobile;

final class AddressPolicy {
    static boolean copyable(String value){
        if(value==null||value.length()>64||!value.matches("[0-9a-fA-F:.]{2,64}"))return false;
        if(value.contains(":")){try{return java.net.InetAddress.getByName(value)!=null;}catch(Exception e){return false;}}
        if(!value.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))return false;
        for(String part:value.split("\\."))if(Integer.parseInt(part)>255)return false;
        return true;
    }
}
