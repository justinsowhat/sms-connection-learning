import java.util.Random;

import org.canova.api.records.reader.RecordReader;
import org.canova.api.records.reader.impl.CSVRecordReader;
import org.canova.api.split.FileSplit;
import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.eval.Evaluation;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.Updater;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.lossfunctions.LossFunctions.LossFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;


public class RecurrentModel {

    private static Logger log = LoggerFactory.getLogger(RecurrentModel.class);
    private static final String FILENAME = "data.txt";

    // RNN dimensions
    public static final int HIDDEN_LAYER_WIDTH = 8;
    public static final int HIDDEN_LAYER_CONT = 1;
    public static final Random r = new Random(7894);

    public static void main(String[] args) throws Exception{
        int numLinesToSkip = 0;
        String delimiter = ",";
        RecordReader recordReader = new CSVRecordReader(numLinesToSkip,delimiter);
        recordReader.initialize(new FileSplit(new ClassPathResource(FILENAME).getFile()));
        final int numInputs = 14;
        int outputNum = 2;
        int iterations = 10000;
        long seed = 123;
        int labelIndex = 0;
        int numClasses = 2;
        int batchSize = 15192;
        DataSetIterator iterator = new RecordReaderDataSetIterator(recordReader,batchSize,labelIndex,numClasses);
        DataSet next = iterator.next();
        next.normalizeZeroMeanZeroUnitVariance();
        SplitTestAndTrain testAndTrain = next.splitTestAndTrain(.80);
        DataSet trainingData = testAndTrain.getTrain();

        NeuralNetConfiguration.Builder builder = new NeuralNetConfiguration.Builder();
        builder.iterations(iterations);
        builder.learningRate(0.1);
        builder.optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT);
        builder.seed(seed);
        builder.biasInit(0);
        builder.miniBatch(false);
        builder.updater(Updater.RMSPROP);
        builder.weightInit(WeightInit.XAVIER);

        NeuralNetConfiguration.ListBuilder listBuilder = builder.list();

        for (int i = 0; i < HIDDEN_LAYER_CONT; i++) {
            GravesLSTM.Builder hiddenLayerBuilder = new GravesLSTM.Builder();
            hiddenLayerBuilder.nIn(i == 0 ? numInputs : HIDDEN_LAYER_WIDTH);
            hiddenLayerBuilder.nOut(HIDDEN_LAYER_WIDTH);
            hiddenLayerBuilder.activation("tanh");
            listBuilder.layer(i, hiddenLayerBuilder.build());
        }

        RnnOutputLayer.Builder outputLayerBuilder = new RnnOutputLayer.Builder(LossFunction.MCXENT);
        outputLayerBuilder.activation("softmax");
        outputLayerBuilder.nIn(HIDDEN_LAYER_WIDTH);
        outputLayerBuilder.nOut(outputNum);
        listBuilder.layer(HIDDEN_LAYER_CONT, outputLayerBuilder.build());

        listBuilder.pretrain(false);
        listBuilder.backprop(true);
        listBuilder.build();

        MultiLayerConfiguration conf = listBuilder.build();
        MultiLayerNetwork net = new MultiLayerNetwork(conf);
        net.init();
        net.setListeners(new ScoreIterationListener(1000));

        DataSet test = testAndTrain.getTest();

        for (int epoch = 0; epoch < 100; epoch++) {

            System.out.println("Epoch " + epoch);

            net.fit(trainingData);
            net.rnnClearPreviousState();

            INDArray output = net.rnnTimeStep(test.getFeatureMatrix());
            Evaluation eval = new Evaluation(2);
            eval.eval(test.getLabels(), output);
            System.out.println(eval.stats());
            System.out.println("F1 on label 1: " + eval.f1(1));
            System.out.println("Precision on label 1: " + eval.precision(1));
            System.out.println("Recall on label 1: " + eval.recall(1));

            }
            System.out.print("\n");

        }

}
