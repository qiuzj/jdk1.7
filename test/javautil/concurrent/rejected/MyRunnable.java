package javautil.concurrent.rejected;
class MyRunnable implements Runnable {
    private String name;
    public MyRunnable(String name) {
        this.name = name;
    }
    @Override
    public void run() {
        try {
            System.out.println(this.name + " is running. Current: " + Thread.currentThread().getName());
            Thread.sleep(100);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}