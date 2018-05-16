# *-* coding: utf-8 *-*
import argparse
import matplotlib.pyplot as plt
import numpy as np
import nltk
import pandas as pd
import sklearn

def get_jaccard_coefficient(s1, s2):
    """returns float jaccard coefficient jacc_coeff with 0 <= jacc_coeff <= 1
    """
    intersection = s1.intersection(s2)
    union = s1.union(s2)
    jacc_coeff = float(len(intersection)) / float(len(union))
    
    return jacc_coeff


def jaccard_model_predictions(filename):
    df = pd.read_csv(filename)
    
    y_true = df['isParaphrase'].apply(float).tolist()
    src_tokens = df['source_sent'].str.decode('utf-8')
    src_tokens = src_tokens.apply(nltk.word_tokenize)
    plag_tokens =  df['plagiat_sent'].str.decode('utf-8')
    plag_tokens = plag_tokens.apply(nltk.word_tokenize)
    
    list_src_sets = src_tokens.apply(set).values
    list_plag_sets = plag_tokens.apply(set).values
    
    assert len(list_src_sets) == len(list_plag_sets)
    
    jacc_distances = []
    for i in range(len(list_src_sets)):
        jacc_distances.append(get_jaccard_coefficient(list_src_sets[i], list_plag_sets[i]))
        
    return jacc_distances, y_true

def plot_roc(tpr, fpr, thresholds):
    plt.figure()
    roc_auc = sklearn.metrics.auc(fpr, tpr)
    
    plt.plot(fpr, tpr, color='darkorange', lw=2, label='ROC curve (area = %0.2f)' % roc_auc)
    plt.plot([0, 1], [0, 1], color='navy', lw=2, linestyle='--')
    plt.xlim([0.0, 1.0])
    plt.ylim([0.0, 1.05])
    plt.xlabel('False Positive Rate')
    plt.ylabel('True Positive Rate')
    plt.title('Receiver operating characteristic example')
    plt.legend(loc="lower right")
    plt.show()
    

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--fn', help='path to data file', type=str, required=True)
    parser.add_argument('--t', help='classificiation threshold', type=float, required=False, default=0.5)

    args = parser.parse_args()
    
    predictions, y_true = jaccard_model_predictions(args.fn)
    pred_array = np.asarray(predictions)
    pred_array[pred_array > args.t] = 1
    pred_array[pred_array<= args.t] = 0
    accuracy = sklearn.metrics.accuracy_score(y_true, pred_array.tolist())
    print("accuracy: {}".format(accuracy))
    
    fpr, tpr, thresholds = sklearn.metrics.roc_curve(y_true, predictions)
    plot_roc(tpr, fpr, thresholds)
    
    