package com.practice.javaprograms.graphs;



import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;


public class ConnectedComponentsBfs {
    public static void main(String[] args) throws IOException, URISyntaxException {
//        BufferedReader br =
//                new BufferedReader(new InputStreamReader(System.in));
        URI uri = Objects.requireNonNull(ClassLoader.getSystemClassLoader().getResource("data/testInput.txt")).toURI();
        BufferedReader br =
                new BufferedReader(new FileReader(Paths.get(uri).toFile()));
        int T = Integer.parseInt(br.readLine().trim());
        while (T-- > 0) {
            String[] s = br.readLine().trim().split(" ");
            int n = Integer.parseInt(s[0]);
            int m = Integer.parseInt(s[1]);
            char[][] grid = new char[n][m];
            for (int i = 0; i < n; i++) {
                String[] S = br.readLine().trim().split(" ");
                for (int j = 0; j < m; j++) {
                    grid[i][j] = S[j].charAt(0);
                }
            }
            Solution obj = new Solution();
            int ans = obj.numIslands(grid);
            System.out.println("No. of connectedComponents : " + ans);
        }
    }
}

class Pair<K,V>{
    K first;
    V second;

    public Pair(K first,V second){
        this.first = first;
        this.second = second;
    }

    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}

class Solution {
    Comparator<Pair<Double,Integer>> comparator = (p1,p2) -> {
        if(p1.first.compareTo(p2.first) == 0){
            return p2.second - p1.second;
        }
        else
            return (int)(p2.first - p1.first);
    };
    private void bfs(int row,int col,int n,int m,boolean[][] vis, char[][] grid){
        Queue<Pair<Integer,Integer>> q = new LinkedList<>();
        q.offer(new Pair<Integer,Integer>(row,col));
        vis[row][col] = true;

        while(!q.isEmpty()){
            int curRow = q.peek().first;
            int curCol = q.peek().second;

            q.poll();

            for(int d1 = -1;d1<=1;d1++){
                for(int d2=-1;d2<=1;d2++){
                    int nRow = curRow + d1;
                    int nCol = curCol + d2;

                    if(nRow >= 0 && nRow<n && nCol>=0 && nCol<m
                            && !vis[nRow][nCol] && grid[nRow][nCol] == '1'){
                        vis[nRow][nCol] = true;
                        q.offer(new Pair<Integer,Integer>(nRow,nCol));
                    }
                }
            }
        }
    }

    // Function to find the number of islands.
    public int numIslands(char[][] grid) {
        // Code here
        int n = grid.length;
        int m = grid[0].length;

        boolean[][] vis = new boolean[n][m];
        int cnt = 0;
        for(int i=0;i<n;i++){
            for(int j=0;j<m;j++){
                if(!vis[i][j] && grid[i][j] == '1'){
                    bfs(i,j,n,m,vis,grid);
                    cnt++;
                }
            }
        }

        return cnt;
    }
}
