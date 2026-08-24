import java.util.ArrayList;
import java.util.List;

public class AddToList {

  public static List<String> addElement(List<String> list, String element) {
    list.add(element);
    return list;
  }

  public static List<String> replaceList(List<String> list) {
    list = new ArrayList<>();
    return list;
  }

  void main() {
    var list = new ArrayList<String>();

    var result = addElement(list, "Java");

    IO.println(list);
    IO.println(result);

    var replacedList = replaceList(list);

    IO.println(list);
    IO.println(replacedList);
  }
}