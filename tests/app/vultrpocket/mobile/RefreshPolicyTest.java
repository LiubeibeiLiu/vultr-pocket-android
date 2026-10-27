package app.vultrpocket.mobile;

public final class RefreshPolicyTest {
    public static void main(String[] args){
        int count=0;
        for(int value:new int[]{0,1,3,5}){if(!RefreshPolicy.validMinutes(value))throw new AssertionError(value);count++;}
        for(int value:new int[]{-1,2,4,10,60,Integer.MAX_VALUE}){if(RefreshPolicy.validMinutes(value))throw new AssertionError(value);count++;}
        if(!RefreshPolicy.succeeded(true,false,false,false,0))throw new AssertionError("fresh success");count++;
        boolean[][] failed={{false,false,false,false},{true,true,false,false},{true,false,true,false},{true,false,false,true}};
        for(boolean[] flags:failed){if(RefreshPolicy.succeeded(flags[0],flags[1],flags[2],flags[3],0))throw new AssertionError("not a fresh success");count++;}
        for(int issues:new int[]{-1,1}){if(RefreshPolicy.succeeded(true,false,false,false,issues))throw new AssertionError("missing or failed data");count++;}
        System.out.println("Refresh policy: "+count+"/"+count+" assertions passed");
    }
}
