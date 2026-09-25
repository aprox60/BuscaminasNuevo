package co.icesi.buscaminas.client.dto;

/**
 * Copia del modelo Cell del servidor. Los nombres de los campos deben coincidir
 * exactamente con los del servidor para que Gson los deserialice.
 */
public class Cell {

    private boolean isLandMine;
    private int value;
    private boolean hide;
    private boolean showAll;
    private boolean isMarked;

    public boolean isLandMine() {
        return isLandMine;
    }

    public int getValue() {
        return value;
    }

    public boolean isHide() {
        return hide;
    }

    public boolean isShowAll() {
        return showAll;
    }

    public boolean isMarked() {
        return isMarked;
    }
}
