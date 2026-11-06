package dev.edgeflow;

public final class SettingsDisplayCheck {
    private static int assertions;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);assertions++;}
    public static void main(String[] args){
        check(SettingsDisplay.vibrationPercent(0)==0,"zero keeps the system-feedback sentinel");
        check(SettingsDisplay.vibrationPercent(1)==1&&SettingsDisplay.vibrationPercent(2)==1,"small explicit amplitudes cannot appear as system feedback");
        check(SettingsDisplay.vibrationPercent(127)==50&&SettingsDisplay.vibrationPercent(128)==50,"existing central amplitude values display fifty percent");
        check(SettingsDisplay.vibrationPercent(255)==100,"maximum amplitude displays one hundred percent");
        boolean bounded=true,ordered=true,sentinel=true,close=true;int previous=-1;
        for(int amplitude=0;amplitude<=255;amplitude++){
            int displayed=SettingsDisplay.vibrationPercent(amplitude);
            bounded&=displayed>=0&&displayed<=100;ordered&=displayed>=previous;
            sentinel&=(displayed==0)==(amplitude==0);
            close&=Math.abs(displayed-amplitude/2.55)<1;
            previous=displayed;
        }
        check(bounded,"all stored amplitudes remain inside the displayed percentage range");
        check(ordered,"display percentage is monotonic across all 256 stored amplitudes");
        check(sentinel,"only the actual zero value displays the system-feedback sentinel");
        check(close,"integer percentage stays within one point of the original strength");
        check(SettingsDisplay.vibrationPercent(-1)==0&&SettingsDisplay.vibrationPercent(256)==100,"invalid presentation input is bounded without changing storage");
        check(SettingsDisplay.number(12.3f,"dp",false).equals("12dp"),"fractional handle width has an integer readout");
        check(SettingsDisplay.number(39.43f,"dp",false).equals("39dp"),"saved recognition threshold is rounded only for display");
        check(SettingsDisplay.number(.8691516f,"%",false).equals("87%"),"fractional geometry uses an integer percentage readout");
        check(SettingsDisplay.number(2.35f,"×",false).equals("235%"),"fractional touchpad speed uses a precise integer percentage readout");
        check(SettingsDisplay.number(300,"ms",true).equals("300ms"),"duration readout has no fractional padding");
        check(SettingsDisplay.number(-25.25f,"°",false).equals("-25°"),"negative angle readout preserves its sign");
        check(SettingsDisplay.number(-.00001f,"°",false).equals("0°"),"rounding cannot display negative zero");
        check(SettingsDisplay.vibrationLevelPercent(0)==0&&SettingsDisplay.vibrationLevelPercent(30)==100,"duration scale endpoints map to zero and one hundred percent");
        check(SettingsDisplay.vibrationLevelPercent(5)==17&&SettingsDisplay.vibrationLevelPercent(15)==50,"default and middle duration levels display their percentage");
        boolean durationOrdered=true;int last=-1;for(int level=0;level<=30;level++){int percent=SettingsDisplay.vibrationLevelPercent(level);durationOrdered&=percent>last;last=percent;}
        check(durationOrdered,"all thirty-one duration levels have a distinct increasing readout");
        boolean noFractions=true;for(int step=0;step<=1000;step++){float raw=2+28*step/1000f;String label=SettingsDisplay.number(raw,"dp",false);noFractions&=label.matches("[0-9]+dp");}
        check(noFractions,"all thousand-step width positions produce integer-only labels");
        check(SettingsDisplay.number(25,"%",true).equals("25%"),"an already integral percentage is not multiplied again");
        check(SettingsDisplay.number(.5f,"×",false).equals("50%")&&SettingsDisplay.number(3,"×",false).equals("300%"),"speed endpoints remain distinct without decimal or multiplier labels");
        System.out.println("PASS "+assertions+" settings presentation assertions, including all 256 stored amplitudes");
    }
}
