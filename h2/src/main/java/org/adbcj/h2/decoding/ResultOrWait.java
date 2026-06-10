package org.adbcj.h2.decoding;


public final class ResultOrWait<T>{
    public final T result;
    public final boolean couldReadResult;

    public final static ResultOrWait WaitLonger = new ResultOrWait(null,false);
    public final static ResultOrWait Start = new ResultOrWait(null,true);


    ResultOrWait(T result, boolean couldReadResult) {
        this.result = result;
        this.couldReadResult = couldReadResult;
    }

    public static <T> ResultOrWait<T> result(T data) {
        return new ResultOrWait<T>(data,true);
    }

    @Override
    public String toString() {
        if(couldReadResult){
            return String.valueOf(result);
        }else{
            return "{No Result}";
        }
    }
}
