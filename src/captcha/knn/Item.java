package captcha.knn;

import java.io.Serializable;

/**
 * @author Mathieu Clément
 * @since 25.12.2013
 */
public class Item<O, I> implements Serializable {

    public Item() {
    }

    public Item(O output, I inputs) {
        this.output = output;
        this.inputs = inputs;
    }

    public I inputs;
    public O output;
}
