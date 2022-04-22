package projects.sanders.models.connectivityModels;

import sinalgo.exception.CorruptConfigurationEntryException;
import sinalgo.models.ConnectivityModelHelper;
import sinalgo.nodes.Node;
import sinalgo.runtime.SinalgoRuntime;

import java.util.HashMap;

public class Coterie extends ConnectivityModelHelper {
    private static boolean initialized;

    public Coterie() throws CorruptConfigurationEntryException {
        if(!initialized) {
            initialized = true;
        }
    }

    // we need connect nodes that are in the same row or column
    //  n1 ------ n2
    //  |         |
    //  n3 ------ n4
    // we can use node id and matrix size to get each node position
    @Override
    protected boolean isConnected(Node from, Node to) {
        HashMap<String,Integer> from_position = nodePosition(from);
        HashMap<String,Integer> to_position = nodePosition(to);

        if(to_position.get("column") == from_position.get("column") || from_position.get("row") == to_position.get("row")) {
            return true;
        }

        return false;
    }

    private HashMap<String,Integer> nodePosition (Node n) {
        HashMap<String,Integer> position = new HashMap<String,Integer>();
        int nodes_quantity = SinalgoRuntime.getNodes().size();
        int matrix_size = (int) Math.sqrt(nodes_quantity);
        int row = (int) ((n.getID() - 1) % matrix_size);
        int column = (int) ((n.getID() - 1) / matrix_size);

        position.put("row", row);
        position.put("column", column);

        return position;
    }
}
