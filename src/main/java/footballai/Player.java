package footballai;

public class Player {
    private String name, nation, pos;
    private double rating;

    public Player(String name, String nation, String pos, double rating) {
        this.name = name; this.nation = nation; this.pos = pos; this.rating = rating;
    }

    public String getName() { return name; }
    public String getNation() { return nation; }
    public String getPos() { return pos; }
    public double getRating() { return rating; }

}