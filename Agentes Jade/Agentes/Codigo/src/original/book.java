package original;

public class book {

	private String name;
	private int price;
	private int units;

	public book(String name, int price, int units) {
		this.name = name;
		this.price = price;
		this.units = units;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getPrice() {
		return price;
	}

	public void setPrice(int price) {
		this.price = price;
	}

	public int getUnits() {
		return units;
	}

	public void setUnits(int units) {
		this.units = units;
	}

	
	public String toString() {
		return "book [name=" + name + ", price=" + price + ", units=" + units + "]";
	}
	

}
