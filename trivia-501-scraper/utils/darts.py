"""
Darts scoring utilities.
"""

def is_valid_darts_score(score: int) -> bool:
    """
    Checks if a score is achievable with 3 darts in a standard 501 game.
    Max score is 180 (T20 * 3).
    Impossible scores: 163, 166, 169, 172, 173, 175, 176, 178, 179.
    """
    if score < 1 or score > 180:
        return False

    # Known impossible scores for 3 darts
    impossible_scores = {163, 166, 169, 172, 173, 175, 176, 178, 179}
    
    if score in impossible_scores:
        return False
        
    return True
